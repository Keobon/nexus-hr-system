package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.assistant.dto.AiDraftRequest;
import com.nexuslabs.hr.domain.assistant.dto.AiDraftResult;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftDiscardResult;
import com.nexuslabs.hr.domain.assistant.dto.AiOvertimeDraftResponse;
import com.nexuslabs.hr.domain.assistant.dto.DraftApprovalStep;
import com.nexuslabs.hr.domain.assistant.entity.AiOvertimeDraft;
import com.nexuslabs.hr.domain.assistant.repository.AiOvertimeDraftRepository;
import com.nexuslabs.hr.domain.attendance.dto.OvertimeCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.OvertimeResponse;
import com.nexuslabs.hr.domain.attendance.service.OvertimeService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 연장근무 결재 초안(2026-11-19). 휴가 초안({@link AiLeaveDraftService})과 같은 흐름 — AI 는 근무일 · 예정 시각 · 사유만
 * 뽑아 초안을 만들고, 검사는 {@link OvertimeService#check}, 신청은 확인했을 때 {@link OvertimeService#create}가 한다.
 * LLM 호출은 DB 트랜잭션 밖에서: 읽기(이전 초안) → LLM → 저장 → 미리보기.
 */
@Service
public class AiOvertimeDraftService {

    static final Duration TTL = Duration.ofMinutes(30);
    private static final int REASON_MAX = 255;

    private final AiOvertimeDraftRepository repository;
    private final LlmClient llm;
    private final AiRateLimiter rateLimiter;
    private final OvertimeService overtimeService;
    private final DraftApprovalPreview approvalPreview;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AiOvertimeDraftService(AiOvertimeDraftRepository repository, LlmClient llm, AiRateLimiter rateLimiter,
                                  OvertimeService overtimeService, DraftApprovalPreview approvalPreview,
                                  TransactionTemplate tx, Clock clock) {
        this.repository = repository;
        this.llm = llm;
        this.rateLimiter = rateLimiter;
        this.overtimeService = overtimeService;
        this.approvalPreview = approvalPreview;
        this.tx = tx;
        this.clock = clock;
    }

    /** 문장으로 초안을 만들거나(draftId 없음) 고친다(draftId 있음). */
    public AiOvertimeDraftResponse draft(LoginUser user, AiDraftRequest request) {
        if (!llm.available()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        Context ctx = tx.execute(s -> context(user, request.draftId()));
        if (ctx.expired()) {
            throw new BusinessException(ErrorCode.AI_DRAFT_EXPIRED);
        }
        rateLimiter.acquire(user.employeeId());
        OvertimeExtraction ex = llm.extractOvertime(
                new OvertimeExtractionPrompt(LocalDate.now(clock), request.message().strip(), ctx.current()));

        Merged m = merge(ctx.current(), ex);
        AiOvertimeDraft saved = tx.execute(s -> save(user, request, m));
        return respond(user, saved, m.question());
    }

    /** 초안을 다시 읽어 토큰의 직원으로 연장근무를 신청한다. 남의 초안은 AI_DRAFT_NOT_FOUND. */
    public OvertimeResponse confirm(LoginUser user, long id) {
        Boolean expired = tx.execute(s -> {
            AiOvertimeDraft d = lockMine(user, id);
            d.requireDraft();
            if (d.expiredAt(now())) {
                d.expire();
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(expired)) {
            throw new BusinessException(ErrorCode.AI_DRAFT_EXPIRED);
        }
        return tx.execute(s -> {
            AiOvertimeDraft d = lockMine(user, id);
            d.requireDraft();
            if (!d.complete()) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "초안에 빠진 정보가 있습니다");
            }
            OvertimeResponse overtime = overtimeService.create(user, new OvertimeCreateRequest(d.getWorkDate(),
                    d.getPlannedStart(), d.getPlannedEnd(), d.getReason()));
            d.confirm(overtime.id());
            return overtime;
        });
    }

    public AiLeaveDraftDiscardResult discard(LoginUser user, long id) {
        return tx.execute(s -> {
            AiOvertimeDraft d = lockMine(user, id);
            d.discard();
            return new AiLeaveDraftDiscardResult(d.getId(), d.getStatus());
        });
    }

    // ------------------------------------------------------------------

    /** 고치는 중이면 그 초안의 지금 값. 만료됐으면 EXPIRED 로 바꿔 두고 알린다. */
    private Context context(LoginUser user, Long draftId) {
        if (draftId == null) {
            return new Context(null, false);
        }
        AiOvertimeDraft d = lockMine(user, draftId);
        d.requireDraft();
        if (d.expiredAt(now())) {
            d.expire();
            return new Context(null, true);
        }
        return new Context(new OvertimeExtractionPrompt.Current(d.getWorkDate(), d.getPlannedStart(),
                d.getPlannedEnd(), d.getReason()), false);
    }

    /**
     * 이번에 뽑은 값(null 이면 지금 초안 값 유지)을 합치고 서버가 다시 검사한다. 빠진 값은 하나씩 서버 문장으로 되묻는다
     * — 날짜 → 시작 시각 → 종료 시각 → 사유 순.
     */
    private Merged merge(OvertimeExtractionPrompt.Current current, OvertimeExtraction ex) {
        OvertimeExtractionPrompt.Current cur = current == null
                ? new OvertimeExtractionPrompt.Current(null, null, null, null)
                : current;
        LocalDate workDate = firstNonNull(DraftValues.date(ex.workDate()), cur.workDate());
        String start = firstNonNull(DraftValues.time(ex.plannedStart()), cur.plannedStart());
        String end = firstNonNull(DraftValues.time(ex.plannedEnd()), cur.plannedEnd());
        String reason = ex.reason() != null ? DraftValues.clean(ex.reason(), REASON_MAX) : cur.reason();

        String question = null;
        if (workDate == null) {
            question = "연장근무 하는 날짜를 알려 주세요. 예) 11/18";
        } else if (!DraftValues.inRange(workDate, LocalDate.now(clock))) {
            question = "날짜를 다시 확인해 주세요. 오늘부터 1년 안팎의 날짜만 신청할 수 있습니다.";
            workDate = null;
        } else if (start == null) {
            question = "몇 시부터 연장근무를 하시나요? 예) 19:00";
        } else if (end == null) {
            question = "몇 시까지 하시나요? 예) 22:00";
        } else if (start.equals(end)) {
            question = "시작과 끝 시각이 같아요. 몇 시부터 몇 시까지인지 다시 알려 주세요.";
            end = null;
        } else if (reason == null) {
            question = "어떤 일로 연장근무를 하시나요? 사유를 알려 주세요.";
        }
        return new Merged(workDate, start, end, reason, question);
    }

    private AiOvertimeDraft save(LoginUser user, AiDraftRequest request, Merged m) {
        AiOvertimeDraft d;
        if (request.draftId() == null) {
            d = new AiOvertimeDraft(user.employeeId());
        } else {
            // LLM 을 부르는 동안 확인 · 폐기됐을 수 있다 — 다시 잠그고 확인한다
            d = lockMine(user, request.draftId());
            d.requireDraft();
        }
        d.fill(request.message().strip(), m.workDate(), m.start(), m.end(), m.reason(), now().plus(TTL));
        return repository.saveAndFlush(d);
    }

    /** 값이 다 모였으면 연장근무 신청과 같은 검사 · 승인선 미리보기로 READY / INVALID 를 정한다. */
    private AiOvertimeDraftResponse respond(LoginUser user, AiOvertimeDraft d, String question) {
        AiOvertimeDraftResponse.Draft draft = new AiOvertimeDraftResponse.Draft(d.getWorkDate(), d.getPlannedStart(),
                d.getPlannedEnd(), d.getReason(), d.getExpiresAt());
        if (question != null || !d.complete()) {
            return new AiOvertimeDraftResponse(d.getId(), AiDraftResult.NEED_INFO,
                    question != null ? question : "빠진 정보를 알려 주세요.", draft, null, List.of());
        }
        OvertimeService.OvertimeCheck check = overtimeService.check(user.companyId(), user.employeeId(),
                d.getWorkDate(), d.getPlannedStart(), d.getPlannedEnd());
        List<String> errors = new ArrayList<>(check.errors().stream().map(e -> e.code().name()).toList());
        List<DraftApprovalStep> steps = approvalPreview.steps(user.companyId(), ApprovalWorkType.OVERTIME,
                user.employeeId(), errors);
        List<String> codes = List.copyOf(errors);
        return new AiOvertimeDraftResponse(d.getId(), codes.isEmpty() ? AiDraftResult.READY : AiDraftResult.INVALID,
                null, draft, new AiOvertimeDraftResponse.Preview(check.minutes(), steps, codes), codes);
    }

    private AiOvertimeDraft lockMine(LoginUser user, long id) {
        return repository.findForUpdate(id)
                .filter(d -> d.getEmployeeId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_DRAFT_NOT_FOUND));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private record Context(OvertimeExtractionPrompt.Current current, boolean expired) {
    }

    private record Merged(LocalDate workDate, String start, String end, String reason, String question) {
    }
}
