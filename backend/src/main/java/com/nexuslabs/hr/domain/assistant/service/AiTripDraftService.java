package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.assistant.dto.AiDraftRequest;
import com.nexuslabs.hr.domain.assistant.dto.AiDraftResult;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftDiscardResult;
import com.nexuslabs.hr.domain.assistant.dto.AiTripDraftResponse;
import com.nexuslabs.hr.domain.assistant.dto.DraftApprovalStep;
import com.nexuslabs.hr.domain.assistant.entity.AiTripDraft;
import com.nexuslabs.hr.domain.assistant.repository.AiTripDraftRepository;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripResponse;
import com.nexuslabs.hr.domain.attendance.entity.TripType;
import com.nexuslabs.hr.domain.attendance.service.BusinessTripService;
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
 * AI 출장 결재 초안(2026-11-19). 휴가 초안({@link AiLeaveDraftService})과 같은 흐름 — AI 는 목적지 · 목적 · 기간 · 출장 종류 ·
 * (말했으면) 예상 비용만 뽑아 초안을 만들고, 검사는 {@link BusinessTripService#check}, 신청은 확인했을 때
 * {@link BusinessTripService#create}가 한다. LLM 호출은 DB 트랜잭션 밖에서.
 */
@Service
public class AiTripDraftService {

    static final Duration TTL = Duration.ofMinutes(30);
    private static final int DESTINATION_MAX = 100;
    private static final int PURPOSE_MAX = 500;
    private static final long COST_MAX = 1_000_000_000_000L;

    private final AiTripDraftRepository repository;
    private final LlmClient llm;
    private final AiRateLimiter rateLimiter;
    private final BusinessTripService tripService;
    private final DraftApprovalPreview approvalPreview;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AiTripDraftService(AiTripDraftRepository repository, LlmClient llm, AiRateLimiter rateLimiter,
                              BusinessTripService tripService, DraftApprovalPreview approvalPreview,
                              TransactionTemplate tx, Clock clock) {
        this.repository = repository;
        this.llm = llm;
        this.rateLimiter = rateLimiter;
        this.tripService = tripService;
        this.approvalPreview = approvalPreview;
        this.tx = tx;
        this.clock = clock;
    }

    /** 문장으로 초안을 만들거나(draftId 없음) 고친다(draftId 있음). */
    public AiTripDraftResponse draft(LoginUser user, AiDraftRequest request) {
        if (!llm.available()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        Context ctx = tx.execute(s -> context(user, request.draftId()));
        if (ctx.expired()) {
            throw new BusinessException(ErrorCode.AI_DRAFT_EXPIRED);
        }
        rateLimiter.acquire(user.employeeId());
        TripExtraction ex = llm.extractTrip(
                new TripExtractionPrompt(LocalDate.now(clock), request.message().strip(), ctx.current()));

        Merged m = merge(ctx.current(), ex);
        AiTripDraft saved = tx.execute(s -> save(user, request, m));
        return respond(user, saved, m.question());
    }

    /** 초안을 다시 읽어 토큰의 직원으로 출장을 신청한다. 남의 초안은 AI_DRAFT_NOT_FOUND. */
    public BusinessTripResponse confirm(LoginUser user, long id) {
        Boolean expired = tx.execute(s -> {
            AiTripDraft d = lockMine(user, id);
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
            AiTripDraft d = lockMine(user, id);
            d.requireDraft();
            if (!d.complete()) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "초안에 빠진 정보가 있습니다");
            }
            BusinessTripResponse trip = tripService.create(user, new BusinessTripCreateRequest(d.getTripType(),
                    d.getDestination(), d.getPurpose(), d.getStartDate(), d.getEndDate(), d.getEstimatedCost()));
            d.confirm(trip.id());
            return trip;
        });
    }

    public AiLeaveDraftDiscardResult discard(LoginUser user, long id) {
        return tx.execute(s -> {
            AiTripDraft d = lockMine(user, id);
            d.discard();
            return new AiLeaveDraftDiscardResult(d.getId(), d.getStatus());
        });
    }

    // ------------------------------------------------------------------

    private Context context(LoginUser user, Long draftId) {
        if (draftId == null) {
            return new Context(null, false);
        }
        AiTripDraft d = lockMine(user, draftId);
        d.requireDraft();
        if (d.expiredAt(now())) {
            d.expire();
            return new Context(null, true);
        }
        return new Context(new TripExtractionPrompt.Current(d.getTripType(), d.getDestination(), d.getPurpose(),
                d.getStartDate(), d.getEndDate(), d.getEstimatedCost()), false);
    }

    /**
     * 이번에 뽑은 값(null 이면 지금 초안 값 유지)을 합치고 서버가 다시 검사한다. 빠진 값은 하나씩 서버 문장으로 되묻는다 —
     * 목적지 → 날짜 → 목적 → 출장 종류 순. 예상 비용은 말했을 때만 채우고 되묻지 않는다.
     */
    private Merged merge(TripExtractionPrompt.Current current, TripExtraction ex) {
        TripExtractionPrompt.Current cur = current == null
                ? new TripExtractionPrompt.Current(null, null, null, null, null, null)
                : current;
        String destination = ex.destination() != null
                ? DraftValues.clean(ex.destination(), DESTINATION_MAX) : cur.destination();
        String purpose = ex.purpose() != null ? DraftValues.clean(ex.purpose(), PURPOSE_MAX) : cur.purpose();

        LocalDate newStart = DraftValues.date(ex.startDate());
        LocalDate newEnd = DraftValues.date(ex.endDate());
        LocalDate start = newStart != null ? newStart : cur.startDate();
        LocalDate end;
        if (newEnd != null) {
            end = newEnd;
        } else if (newStart != null && !ex.missing().contains("endDate")) {
            end = newStart;   // 시작일만 새로 말하면 하루짜리
        } else {
            end = cur.endDate();
        }

        TripType tripType = tripType(ex.tripType());
        if (tripType == null) {
            tripType = cur.tripType();
        }
        Long cost = ex.estimatedCost() != null && ex.estimatedCost() >= 0 && ex.estimatedCost() <= COST_MAX
                ? ex.estimatedCost() : cur.estimatedCost();

        String question = null;
        LocalDate today = LocalDate.now(clock);
        if (destination == null) {
            question = "어디로 출장 가시나요?";
        } else if (start == null) {
            question = "출장 날짜를 알려 주세요. 예) 11/20~11/21";
        } else if (end == null) {
            question = "언제까지 출장인가요?";
        } else if (start.isAfter(end)) {
            question = "끝나는 날이 시작일보다 빠릅니다. 날짜를 다시 알려 주세요.";
            end = null;
        } else if (!DraftValues.inRange(start, today) || !DraftValues.inRange(end, today)) {
            question = "날짜를 다시 확인해 주세요. 오늘부터 1년 안팎의 날짜만 신청할 수 있습니다.";
            start = null;
            end = null;
        } else if (purpose == null) {
            question = "출장 목적을 알려 주세요.";
        } else if (tripType == null) {
            question = "국내 출장인가요, 해외 출장인가요?";
        }
        return new Merged(tripType, destination, purpose, start, end, cost, question);
    }

    private AiTripDraft save(LoginUser user, AiDraftRequest request, Merged m) {
        AiTripDraft d;
        if (request.draftId() == null) {
            d = new AiTripDraft(user.employeeId());
        } else {
            d = lockMine(user, request.draftId());
            d.requireDraft();
        }
        d.fill(request.message().strip(), m.tripType(), m.destination(), m.purpose(), m.start(), m.end(), m.cost(),
                now().plus(TTL));
        return repository.saveAndFlush(d);
    }

    /** 값이 다 모였으면 출장 신청과 같은 검사 · 승인선 미리보기로 READY / INVALID 를 정한다. */
    private AiTripDraftResponse respond(LoginUser user, AiTripDraft d, String question) {
        AiTripDraftResponse.Draft draft = new AiTripDraftResponse.Draft(d.getTripType(), d.getDestination(),
                d.getPurpose(), d.getStartDate(), d.getEndDate(), d.getEstimatedCost(), d.getExpiresAt());
        if (question != null || !d.complete()) {
            return new AiTripDraftResponse(d.getId(), AiDraftResult.NEED_INFO,
                    question != null ? question : "빠진 정보를 알려 주세요.", draft, null, List.of());
        }
        BusinessTripService.TripCheck check = tripService.check(user.companyId(), user.employeeId(), d.getStartDate(),
                d.getEndDate());
        List<String> errors = new ArrayList<>(check.errors().stream().map(e -> e.code().name()).toList());
        List<DraftApprovalStep> steps = approvalPreview.steps(user.companyId(), ApprovalWorkType.BUSINESS_TRIP,
                user.employeeId(), errors);
        List<String> codes = List.copyOf(errors);
        return new AiTripDraftResponse(d.getId(), codes.isEmpty() ? AiDraftResult.READY : AiDraftResult.INVALID,
                null, draft, new AiTripDraftResponse.Preview(check.days(), steps, codes), codes);
    }

    private AiTripDraft lockMine(LoginUser user, long id) {
        return repository.findForUpdate(id)
                .filter(d -> d.getEmployeeId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_DRAFT_NOT_FOUND));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** DOMESTIC · OVERSEAS 가 아니면 null(모름). */
    private static TripType tripType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TripType.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record Context(TripExtractionPrompt.Current current, boolean expired) {
    }

    private record Merged(TripType tripType, String destination, String purpose, LocalDate start, LocalDate end,
                          Long cost, String question) {
    }
}
