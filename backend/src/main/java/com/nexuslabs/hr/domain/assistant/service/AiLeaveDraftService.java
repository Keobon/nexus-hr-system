package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftDiscardResult;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftRequest;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftResponse;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftResponse.Status;
import com.nexuslabs.hr.domain.assistant.entity.AiLeaveDraft;
import com.nexuslabs.hr.domain.assistant.repository.AiLeaveDraftRepository;
import com.nexuslabs.hr.domain.leave.dto.LeavePreview;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestCreate;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestResponse;
import com.nexuslabs.hr.domain.leave.entity.HalfDay;
import com.nexuslabs.hr.domain.leave.service.LeaveRequestService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 휴가 결재 초안(2026-11-16). AI 는 문장에서 종류 · 날짜 · 반차 · 사유를 뽑아 초안만 만든다.
 * 검사와 신청은 기존 휴가 로직이 한다 — 초안마다 {@link LeaveRequestService#preview}, 확인하면 {@link LeaveRequestService#create}.
 * <p>
 * LLM 호출(최대 수 초)은 DB 트랜잭션 밖에서 한다. 그래서 트랜잭션을 {@link TransactionTemplate}으로 나눈다:
 * 읽기(이전 초안 · 휴가 종류) → LLM → 저장 → 미리보기. 미리보기는 업무 오류를 던질 수 있어 저장과 다른 트랜잭션에서 부른다.
 */
@Service
public class AiLeaveDraftService {

    static final Duration TTL = Duration.ofMinutes(30);
    private static final int REASON_MAX = 255;

    private final AiLeaveDraftRepository repository;
    private final LlmClient llm;
    private final AiRateLimiter rateLimiter;
    private final LeaveRequestService leaveRequestService;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AiLeaveDraftService(AiLeaveDraftRepository repository, LlmClient llm, AiRateLimiter rateLimiter,
                               LeaveRequestService leaveRequestService, JdbcTemplate jdbc, TransactionTemplate tx,
                               Clock clock) {
        this.repository = repository;
        this.llm = llm;
        this.rateLimiter = rateLimiter;
        this.leaveRequestService = leaveRequestService;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    /** 문장으로 초안을 만들거나(draftId 없음) 고친다(draftId 있음). */
    public AiLeaveDraftResponse draft(LoginUser user, AiLeaveDraftRequest request) {
        if (!llm.available()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        Context ctx = tx.execute(s -> context(user, request.draftId()));
        if (ctx.expired()) {
            throw new BusinessException(ErrorCode.AI_DRAFT_EXPIRED);
        }
        rateLimiter.acquire(user.employeeId());
        LeaveExtraction ex = llm.extractLeave(new LeaveExtractionPrompt(LocalDate.now(clock),
                List.copyOf(ctx.types().values()), request.message().strip(), ctx.current()));

        Merged m = merge(ctx, ex);
        AiLeaveDraft saved = tx.execute(s -> save(user, request, m));
        return respond(user, saved, m.typeName(), m.question());
    }

    /** 초안을 다시 읽어 토큰의 직원으로 휴가를 신청한다. 남의 초안은 AI_DRAFT_NOT_FOUND. */
    public LeaveRequestResponse confirm(LoginUser user, long id) {
        Boolean expired = tx.execute(s -> {
            AiLeaveDraft d = lockMine(user, id);
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
            AiLeaveDraft d = lockMine(user, id);
            d.requireDraft();
            if (!d.complete()) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "초안에 빠진 정보가 있습니다");
            }
            LeaveRequestResponse leave = leaveRequestService.create(user, new LeaveRequestCreate(d.getLeaveTypeId(),
                    d.getStartDate(), d.getEndDate(), d.getReason(), d.getHalfDay()));
            d.confirm(leave.id());
            return leave;
        });
    }

    public AiLeaveDraftDiscardResult discard(LoginUser user, long id) {
        return tx.execute(s -> {
            AiLeaveDraft d = lockMine(user, id);
            d.discard();
            return new AiLeaveDraftDiscardResult(d.getId(), d.getStatus());
        });
    }

    // ------------------------------------------------------------------

    /** 회사의 활성 휴가 종류와, 고치는 중이면 그 초안의 지금 값. 만료됐으면 EXPIRED 로 바꿔 두고 알린다. */
    private Context context(LoginUser user, Long draftId) {
        Map<Long, String> types = new LinkedHashMap<>();
        jdbc.query("SELECT id, name FROM leave_type WHERE company_id = ? AND is_active ORDER BY sort_order, id",
                rs -> {
                    types.put(rs.getLong("id"), rs.getString("name"));
                },
                user.companyId());
        if (draftId == null) {
            return new Context(types, null, false);
        }
        AiLeaveDraft d = lockMine(user, draftId);
        d.requireDraft();
        if (d.expiredAt(now())) {
            d.expire();
            return new Context(types, null, true);
        }
        return new Context(types, new LeaveExtractionPrompt.Current(types.get(d.getLeaveTypeId()), d.getStartDate(),
                d.getEndDate(), d.getHalfDay(), d.getReason()), false);
    }

    /**
     * 이번에 뽑은 값(null 이면 지금 초안 값 유지)을 합치고 서버가 다시 검사한다. 빠진 값이 있으면 question 에 되물을 말.
     * AI 가 준 question 은 쓰지 않는다 — 화면에 AI 가 쓴 글이 그대로 나가지 않게 서버 문장으로 묻는다.
     */
    private Merged merge(Context ctx, LeaveExtraction ex) {
        LeaveExtractionPrompt.Current cur = ctx.current() == null
                ? new LeaveExtractionPrompt.Current(null, null, null, null, null)
                : ctx.current();

        String typeName = ex.leaveTypeName() != null ? ex.leaveTypeName().strip() : cur.leaveTypeName();
        Long typeId = typeName == null ? null : ctx.types().entrySet().stream()
                .filter(e -> sameName(e.getValue(), typeName))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        String knownTypeName = typeId == null ? null : ctx.types().get(typeId);

        LocalDate newStart = date(ex.startDate());
        LocalDate newEnd = date(ex.endDate());
        LocalDate start = newStart != null ? newStart : cur.startDate();
        LocalDate end;
        if (newEnd != null) {
            end = newEnd;
        } else if (newStart != null && !ex.missing().contains("endDate")) {
            end = newStart;   // 시작일만 새로 말하면 하루짜리
        } else {
            end = cur.endDate();
        }

        HalfDay halfDay = cur.halfDay();
        if (ex.halfDay() != null) {
            switch (ex.halfDay().strip().toUpperCase()) {
                case "AM" -> halfDay = HalfDay.AM;
                case "PM" -> halfDay = HalfDay.PM;
                case "NONE" -> halfDay = null;
                default -> { }   // 모르는 값은 무시
            }
        }

        String reason = ex.reason() != null ? clean(ex.reason()) : cur.reason();

        String question = null;
        LocalDate today = LocalDate.now(clock);
        if (typeId == null) {
            String list = String.join(", ", ctx.types().values());
            question = typeName == null
                    ? "어떤 휴가로 신청할까요? (" + list + ")"
                    : "'" + typeName + "' 휴가 종류는 없습니다. " + list + " 중에서 골라 주세요.";
        } else if (start == null) {
            question = "언제 쉬실 건가요? 날짜를 알려 주세요.";
        } else if (end == null) {
            question = "언제까지 쉬실 건가요?";
        } else if (start.isAfter(end)) {
            question = "끝나는 날이 시작일보다 빠릅니다. 날짜를 다시 알려 주세요.";
            end = null;
        } else if (start.isBefore(today.minusYears(1)) || end.isAfter(today.plusYears(1))) {
            question = "날짜를 다시 확인해 주세요. 오늘부터 1년 안팎의 날짜만 신청할 수 있습니다.";
            start = null;
            end = null;
        } else if (halfDay != null && !start.equals(end)) {
            question = "반차는 하루만 쓸 수 있습니다. 반차를 쓸 날짜 하루를 알려 주세요.";
        } else if (halfDay == null && ex.missing().contains("halfDay")) {
            question = "오전 반차인가요, 오후 반차인가요?";
        }
        return new Merged(typeId, knownTypeName != null ? knownTypeName : typeName, start, end, halfDay, reason,
                question);
    }

    private AiLeaveDraft save(LoginUser user, AiLeaveDraftRequest request, Merged m) {
        AiLeaveDraft d;
        if (request.draftId() == null) {
            d = new AiLeaveDraft(user.employeeId());
        } else {
            // LLM 을 부르는 동안 확인 · 폐기됐을 수 있다 — 다시 잠그고 확인한다
            d = lockMine(user, request.draftId());
            d.requireDraft();
        }
        d.fill(request.message().strip(), m.typeId(), m.start(), m.end(), m.halfDay(), m.reason(), now().plus(TTL));
        return repository.saveAndFlush(d);
    }

    /** 값이 다 모였으면 기존 미리보기로 검사한다. 미리보기가 던지는 입력 오류(반차 불가 종류 등)도 INVALID 로 돌려준다. */
    private AiLeaveDraftResponse respond(LoginUser user, AiLeaveDraft d, String typeName, String question) {
        AiLeaveDraftResponse.Draft draft = new AiLeaveDraftResponse.Draft(d.getLeaveTypeId(), typeName,
                d.getStartDate(), d.getEndDate(), d.getHalfDay(), d.getReason(), d.getExpiresAt());
        if (question != null || !d.complete()) {
            return new AiLeaveDraftResponse(d.getId(), Status.NEED_INFO,
                    question != null ? question : "빠진 정보를 알려 주세요.", draft, null, List.of());
        }
        try {
            LeavePreview preview = leaveRequestService.preview(user, d.getLeaveTypeId(), d.getStartDate(),
                    d.getEndDate(), d.getHalfDay());
            return new AiLeaveDraftResponse(d.getId(), preview.errors().isEmpty() ? Status.READY : Status.INVALID,
                    null, draft, preview, preview.errors());
        } catch (BusinessException e) {
            String message = e.fields() == null || e.fields().isEmpty()
                    ? e.getMessage()
                    : e.fields().values().iterator().next();
            return new AiLeaveDraftResponse(d.getId(), Status.INVALID, message, draft, null,
                    List.of(e.code().name()));
        }
    }

    private AiLeaveDraft lockMine(LoginUser user, long id) {
        return repository.findForUpdate(id)
                .filter(d -> d.getEmployeeId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_DRAFT_NOT_FOUND));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static boolean sameName(String a, String b) {
        return a.replace(" ", "").equalsIgnoreCase(b.replace(" ", ""));
    }

    /** YYYY-MM-DD 가 아니면 null(모름). */
    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** 제어 문자를 빼고 앞뒤 공백을 지우고 255자로 자른다. 비면 null. */
    private static String clean(String reason) {
        String s = reason.replaceAll("\\p{Cntrl}", " ").strip();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > REASON_MAX ? s.substring(0, REASON_MAX) : s;
    }

    private record Context(Map<Long, String> types, LeaveExtractionPrompt.Current current, boolean expired) {
    }

    private record Merged(Long typeId, String typeName, LocalDate start, LocalDate end, HalfDay halfDay, String reason,
                          String question) {
    }
}
