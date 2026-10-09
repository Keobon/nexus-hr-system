package com.nexuslabs.hr.domain.approval.service;

import com.nexuslabs.hr.domain.approval.dto.ApprovalDecisionRequest;
import com.nexuslabs.hr.domain.approval.dto.HistoryItem;
import com.nexuslabs.hr.domain.approval.dto.InboxItem;
import com.nexuslabs.hr.domain.approval.dto.ReassignItem;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 승인 엔진(F-APPR-02·03·04, BR-APPR-001–006). 휴가 · 휴가 취소 · 연장근무 · 출장 · 출장 경비가 모두 이것을 쓴다.
 *
 * <p>신청 영역이 부르는 메서드 — {@link #preview} · {@link #open} · {@link #openLeaveCancel} · {@link #withdraw} ·
 * {@link #steps} · {@link #markReassignNeeded}. 업무 확정은 {@link ApprovalTarget} 콜백으로 같은 트랜잭션에서 일어난다.
 */
@Service
public class ApprovalService {

    private final JdbcTemplate jdbc;
    private final ApproverCalculator calculator;
    private final ApprovalTargetRegistry targets;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public ApprovalService(JdbcTemplate jdbc, ApproverCalculator calculator, ApprovalTargetRegistry targets,
                           AuditLogger auditLogger, Clock clock) {
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.targets = targets;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // 신청 영역이 부르는 메서드
    // ------------------------------------------------------------------

    /** 신청 화면 미리보기 — 저장하지 않는다. 승인자를 못 찾으면 APPROVER_NOT_FOUND. */
    @Transactional(readOnly = true)
    public ApprovalPlan preview(long companyId, ApprovalWorkType workType, long applicantId) {
        return calculator.plan(companyId, workType, applicantId);
    }

    /**
     * 신청을 저장한 직후 같은 트랜잭션에서 부른다. 승인자를 계산해 단계를 저장하고(BR-APPR-006),
     * 모든 단계가 생략되면 바로 onFinalApproved 를 부른다.
     *
     * @return true 면 즉시 승인됐다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean open(long companyId, ApprovalWorkType workType, long targetId, long applicantId) {
        ApprovalPlan plan = calculator.plan(companyId, workType, applicantId);
        saveSteps(companyId, workType, targetId, plan);
        if (plan.immediatelyApproved()) {
            targets.get(workType).onFinalApproved(targetId, null);
            return true;
        }
        return false;
    }

    /**
     * 승인된 휴가의 취소 요청(BR-LEAVE-004). 승인선 없이 원래 휴가의 마지막 승인자 1단계.
     * 원래 휴가가 즉시 승인이었으면(승인자 없음) 취소도 바로 승인된다.
     * 그 승인자가 지금 후보가 아니면(퇴직·비활성) 재지정 필요로 표시한다.
     *
     * @return true 면 즉시 승인됐다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean openLeaveCancel(long companyId, long leaveRequestId, long applicantId) {
        Long lastApprover = jdbc.query("""
                        SELECT approver_id FROM approval_step
                        WHERE company_id = ? AND work_type = 'LEAVE' AND target_id = ? AND status = 'APPROVED'
                        ORDER BY round DESC, step_order DESC LIMIT 1
                        """,
                (rs, i) -> rs.getLong("approver_id"), companyId, leaveRequestId).stream().findFirst().orElse(null);
        int round = nextRound(companyId, ApprovalWorkType.LEAVE_CANCEL, leaveRequestId);
        if (lastApprover == null || lastApprover == applicantId) {
            insertStep(companyId, ApprovalWorkType.LEAVE_CANCEL, leaveRequestId, null, round, 1, lastApprover,
                    ApprovalStepStatus.SKIPPED, false);
            targets.get(ApprovalWorkType.LEAVE_CANCEL).onFinalApproved(leaveRequestId, null);
            return true;
        }
        insertStep(companyId, ApprovalWorkType.LEAVE_CANCEL, leaveRequestId, null, round, 1, lastApprover,
                ApprovalStepStatus.PENDING, !calculator.isCandidate(companyId, lastApprover));
        return false;
    }

    /** 신청자가 승인대기 신청을 철회했거나 퇴직으로 자동 취소될 때 — 남은 단계를 CANCELLED 로. 신청 상태는 신청 영역이 바꾼다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void withdraw(long companyId, ApprovalWorkType workType, long targetId) {
        jdbc.update("""
                        UPDATE approval_step SET status = 'CANCELLED', needs_reassign = FALSE
                        WHERE company_id = ? AND work_type = ?::approval_work_type AND target_id = ?
                          AND status IN ('PENDING', 'WAITING')
                        """,
                companyId, workType.name(), targetId);
    }

    /** 신청 상세의 approvalSteps. 모든 회차를 순서대로. */
    @Transactional(readOnly = true)
    public List<ApprovalStepView> steps(long companyId, ApprovalWorkType workType, long targetId, long viewerId) {
        return jdbc.query(STEP_SELECT + """
                         WHERE s.company_id = ? AND s.work_type = ?::approval_work_type AND s.target_id = ?
                         ORDER BY s.round, s.step_order
                        """,
                stepMapper(viewerId), companyId, workType.name(), targetId);
    }

    /**
     * 목록 행마다 붙일 현재 승인 진행을 쿼리 한 번으로 모은다(API 설계서 8장 "휴가 신청 목록 행", 역할 분담 v2 2.1).
     * 가장 최근 회차에서 — 승인대기 단계가 있으면 그 단계, 없으면 마지막으로 처리(승인·반려)된 단계.
     * 휴가(LEAVE)는 취소 요청 단계(LEAVE_CANCEL)가 있으면 그쪽이 최근 회차다. 단 반려된 취소 요청은 건너뛴다 —
     * 휴가가 다시 승인완료이므로 원래 휴가의 마지막 승인 단계를 보여 준다(API 8장, 10/10 합의).
     * 처리된 단계가 하나도 없으면(모든 단계 생략 = 즉시 승인, 아무도 처리하기 전 철회) 맵에 넣지 않는다 → 화면은 null.
     */
    @Transactional(readOnly = true)
    public Map<Long, CurrentStep> currentSteps(long companyId, ApprovalWorkType workType, Collection<Long> targetIds) {
        Map<Long, CurrentStep> result = new HashMap<>();
        if (targetIds.isEmpty()) {
            return result;
        }
        String[] workTypes = workType == ApprovalWorkType.LEAVE
                ? new String[]{ApprovalWorkType.LEAVE.name(), ApprovalWorkType.LEAVE_CANCEL.name()}
                : new String[]{workType.name()};
        jdbc.query("""
                        WITH steps AS (
                            SELECT s.target_id, s.step_order, s.status, s.approver_id,
                                   count(*) OVER (PARTITION BY s.target_id, s.work_type, s.round) AS total_steps,
                                   dense_rank() OVER (PARTITION BY s.target_id
                                                      ORDER BY (s.work_type = 'LEAVE_CANCEL') DESC, s.round DESC) AS attempt
                            FROM approval_step s
                            WHERE s.company_id = ? AND s.work_type = ANY(?::approval_work_type[]) AND s.target_id = ANY(?)
                              AND NOT (s.work_type = 'LEAVE_CANCEL' AND s.status = 'REJECTED')
                        ), picked AS (
                            SELECT DISTINCT ON (target_id) target_id, step_order, status, approver_id, total_steps
                            FROM steps
                            WHERE attempt = 1 AND status IN ('PENDING', 'APPROVED', 'REJECTED')
                            ORDER BY target_id, (status = 'PENDING') DESC, step_order DESC
                        )
                        SELECT p.target_id, p.step_order, p.total_steps, p.status::text AS status, e.name AS approver_name
                        FROM picked p
                        LEFT JOIN employee e ON e.id = p.approver_id AND e.company_id = ?
                        """,
                rs -> {
                    result.put(rs.getLong("target_id"), new CurrentStep(rs.getInt("step_order"),
                            rs.getInt("total_steps"), rs.getString("approver_name"),
                            ApprovalStepStatus.valueOf(rs.getString("status"))));
                },
                companyId, workTypes, targetIds.toArray(Long[]::new), companyId);
        return result;
    }

    /** 승인자가 퇴직·비활성이 됐을 때(B-10 퇴직 처리, 계정 비활성화) — 대기 중인 단계를 재지정 필요로 표시. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markReassignNeeded(long companyId, long employeeId) {
        jdbc.update("""
                        UPDATE approval_step SET needs_reassign = TRUE
                        WHERE company_id = ? AND approver_id = ? AND status IN ('PENDING', 'WAITING')
                        """,
                companyId, employeeId);
    }

    /** 비활성 계정을 다시 활성화했을 때 — 다시 승인자 후보가 됐으면 그 사람의 대기 단계에서 재지정 필요 표시를 지운다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clearReassignNeeded(long companyId, long employeeId) {
        if (!calculator.isCandidate(companyId, employeeId)) {
            return;
        }
        jdbc.update("""
                        UPDATE approval_step SET needs_reassign = FALSE
                        WHERE company_id = ? AND approver_id = ? AND status IN ('PENDING', 'WAITING') AND needs_reassign
                        """,
                companyId, employeeId);
    }

    // ------------------------------------------------------------------
    // 승인함 (API 설계서 9.2)
    // ------------------------------------------------------------------

    /** 승인. 다음 단계로 넘기거나, 마지막이면 업무 확정까지 한 트랜잭션(BR-APPR-005). */
    @Transactional
    public ApprovalStepView approve(LoginUser user, long stepId, ApprovalDecisionRequest request) {
        Step step = lockMyTurn(user, stepId);
        Integer approvedMinutes = approvedMinutes(user.companyId(), step, request.approvedMinutes());
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.update("""
                        UPDATE approval_step SET status = 'APPROVED', comment = ?, approved_minutes = ?, acted_at = ?
                        WHERE id = ? AND company_id = ?
                        """,
                blankToNull(request.comment()), approvedMinutes, now, stepId, user.companyId());

        Optional<Long> next = jdbc.query("""
                        SELECT id FROM approval_step
                        WHERE company_id = ? AND work_type = ?::approval_work_type AND target_id = ? AND round = ?
                          AND status = 'WAITING'
                        ORDER BY step_order LIMIT 1
                        """,
                (rs, i) -> rs.getLong("id"),
                user.companyId(), step.workType().name(), step.targetId(), step.round()).stream().findFirst();
        if (next.isPresent()) {
            jdbc.update("UPDATE approval_step SET status = 'PENDING' WHERE id = ? AND company_id = ?",
                    next.get(), user.companyId());
        } else {
            targets.get(step.workType()).onFinalApproved(step.targetId(), view(user, stepId));
        }
        return view(user, stepId);
    }

    /** 반려 — 그 단계 REJECTED, 남은 단계 CANCELLED, 신청 반려(onRejected). */
    @Transactional
    public ApprovalStepView reject(LoginUser user, long stepId, ApprovalDecisionRequest request) {
        if (request.comment() == null || request.comment().isBlank()) {
            throw BusinessException.invalidFields(Map.of("comment", "반려 사유를 입력하세요"));
        }
        if (request.approvedMinutes() != null) {
            throw BusinessException.invalidFields(Map.of("approvedMinutes", "반려에는 인정 시간을 보내지 않습니다"));
        }
        Step step = lockMyTurn(user, stepId);
        jdbc.update("""
                        UPDATE approval_step SET status = 'REJECTED', comment = ?, acted_at = ?
                        WHERE id = ? AND company_id = ?
                        """,
                request.comment().trim(), OffsetDateTime.now(clock), stepId, user.companyId());
        jdbc.update("""
                        UPDATE approval_step SET status = 'CANCELLED'
                        WHERE company_id = ? AND work_type = ?::approval_work_type AND target_id = ? AND round = ?
                          AND status = 'WAITING'
                        """,
                user.companyId(), step.workType().name(), step.targetId(), step.round());
        targets.get(step.workType()).onRejected(step.targetId());
        return view(user, stepId);
    }

    /** 내 차례(PENDING)인 단계. 오래된 신청부터. */
    @Transactional(readOnly = true)
    public PageImpl<InboxItem> inbox(LoginUser user, ApprovalWorkType workType, Pageable pageable) {
        String where = " WHERE s.company_id = ? AND s.approver_id = ? AND s.status = 'PENDING'"
                + (workType == null ? "" : " AND s.work_type = ?::approval_work_type");
        List<Object> args = new ArrayList<>(List.of(user.companyId(), user.employeeId()));
        if (workType != null) {
            args.add(workType.name());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM approval_step s" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<Step> steps = jdbc.query(RAW_STEP_SELECT + where + " ORDER BY s.created_at, s.id LIMIT ? OFFSET ?",
                RAW_STEP_MAPPER, pageArgs.toArray());

        // 요약 · 신청자 · 회차 단계는 페이지 전체를 한 번씩 읽는다(N+1 방지)
        Map<Step, TargetSummary> summaries = summaries(steps);
        Map<Long, Applicant> applicants = applicants(user.companyId(),
                summaries.values().stream().map(TargetSummary::applicantId).toList());
        Map<RoundKey, List<ApprovalStepView>> rounds = rounds(user, steps);
        List<InboxItem> items = new ArrayList<>();
        for (Step s : steps) {
            TargetSummary summary = summaries.get(s);
            Applicant applicant = applicants.get(summary.applicantId());
            List<ApprovalStepView> round = rounds.getOrDefault(RoundKey.of(s), List.of());
            items.add(new InboxItem(s.id(), s.workType(), s.targetId(), applicant.id(), applicant.name(),
                    applicant.orgUnitName(), summary.title(), summary.details(), summary.requestedMinutes(),
                    s.stepOrder(), round.size(),
                    round.stream().filter(r -> r.stepOrder() < s.stepOrder()).toList()));
        }
        return new PageImpl<>(items, pageable, total);
    }

    /** 내가 처리한(승인·반려) 단계. 최근 처리부터. */
    @Transactional(readOnly = true)
    public PageImpl<HistoryItem> history(LoginUser user, Pageable pageable) {
        String where = " WHERE s.company_id = ? AND s.approver_id = ? AND s.status IN ('APPROVED', 'REJECTED')";
        long total = jdbc.queryForObject("SELECT count(*) FROM approval_step s" + where, Long.class,
                user.companyId(), user.employeeId());
        List<Step> steps = jdbc.query(RAW_STEP_SELECT + where + " ORDER BY s.acted_at DESC, s.id DESC LIMIT ? OFFSET ?",
                RAW_STEP_MAPPER, user.companyId(), user.employeeId(), pageable.getPageSize(), pageable.getOffset());
        Map<Step, TargetSummary> summaries = summaries(steps);
        Map<Long, Applicant> applicants = applicants(user.companyId(),
                summaries.values().stream().map(TargetSummary::applicantId).toList());
        Map<Long, ApprovalStepView> views = views(user, steps);
        List<HistoryItem> items = new ArrayList<>();
        for (Step s : steps) {
            TargetSummary summary = summaries.get(s);
            Applicant applicant = applicants.get(summary.applicantId());
            ApprovalStepView v = views.get(s.id());
            items.add(new HistoryItem(s.id(), s.workType(), s.targetId(), applicant.id(), applicant.name(),
                    summary.title(), v.status(), v.approvedMinutes(), v.comment(), v.actedAt()));
        }
        return new PageImpl<>(items, pageable, total);
    }

    /** 승인자 재지정이 필요한 대기 단계(APPROVAL_MANAGE). 오래된 단계부터, 페이징(10/10 합의 ⑥). */
    @Transactional(readOnly = true)
    public PageImpl<ReassignItem> reassignNeeded(LoginUser user, Pageable pageable) {
        String where = " WHERE s.company_id = ? AND s.needs_reassign AND s.status IN ('PENDING', 'WAITING')";
        long total = jdbc.queryForObject("SELECT count(*) FROM approval_step s" + where, Long.class, user.companyId());
        List<Step> steps = jdbc.query(RAW_STEP_SELECT + where + " ORDER BY s.created_at, s.id LIMIT ? OFFSET ?",
                RAW_STEP_MAPPER, user.companyId(), pageable.getPageSize(), pageable.getOffset());
        Map<Step, TargetSummary> summaries = summaries(steps);
        Map<Long, Applicant> applicants = applicants(user.companyId(),
                summaries.values().stream().map(TargetSummary::applicantId).toList());
        Map<Long, ApprovalStepView> views = views(user, steps);
        List<ReassignItem> items = new ArrayList<>();
        for (Step s : steps) {
            TargetSummary summary = summaries.get(s);
            Applicant applicant = applicants.get(summary.applicantId());
            ApprovalStepView v = views.get(s.id());
            items.add(new ReassignItem(s.id(), s.workType(), s.targetId(), applicant.id(), applicant.name(),
                    summary.title(), s.stepOrder(), v.status(), v.approverId(), v.approverName()));
        }
        return new PageImpl<>(items, pageable, total);
    }

    /** 승인자 재지정 — 재직·활성 직원으로, 신청자 본인은 안 된다. 재지정 필요 표시를 지운다. */
    @Transactional
    public ApprovalStepView reassign(LoginUser user, long stepId, long approverId) {
        Step step = lock(user.companyId(), stepId);
        if (step.status() != ApprovalStepStatus.PENDING && step.status() != ApprovalStepStatus.WAITING) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_DONE);
        }
        // 본문의 ID가 다른 회사거나 없는 직원이면 404(API 설계서 1.5)
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee WHERE id = ? AND company_id = ?)",
                Boolean.class, approverId, user.companyId());
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!calculator.isCandidate(user.companyId(), approverId)) {
            throw BusinessException.invalidFields(Map.of("approverId", "재직중이고 계정이 활성인 직원만 승인자로 지정할 수 있습니다"));
        }
        long applicantId = targets.get(step.workType()).summary(step.targetId()).applicantId();
        if (approverId == applicantId) {
            throw BusinessException.invalidFields(Map.of("approverId", "신청자 본인은 승인자로 지정할 수 없습니다"));
        }
        jdbc.update("UPDATE approval_step SET approver_id = ?, needs_reassign = FALSE WHERE id = ? AND company_id = ?",
                approverId, stepId, user.companyId());
        auditLogger.log(user, AuditAction.UPDATE, "APPROVAL_STEP", stepId,
                Map.of("approverId", step.approverId() == null ? "" : step.approverId()),
                Map.of("approverId", approverId));
        return view(user, stepId);
    }

    // ------------------------------------------------------------------

    private void saveSteps(long companyId, ApprovalWorkType workType, long targetId, ApprovalPlan plan) {
        int round = nextRound(companyId, workType, targetId);
        boolean pendingAssigned = false;
        for (ApprovalPlan.PlannedStep p : plan.steps()) {
            ApprovalStepStatus status;
            if (p.skipped()) {
                status = ApprovalStepStatus.SKIPPED;
            } else if (!pendingAssigned) {
                status = ApprovalStepStatus.PENDING;
                pendingAssigned = true;
            } else {
                status = ApprovalStepStatus.WAITING;
            }
            insertStep(companyId, workType, targetId, plan.approvalLineId(), round, p.stepOrder(), p.approverId(),
                    status, false);
        }
    }

    private void insertStep(long companyId, ApprovalWorkType workType, long targetId, Long lineId, int round,
                            int stepOrder, Long approverId, ApprovalStepStatus status, boolean needsReassign) {
        jdbc.update("""
                        INSERT INTO approval_step (company_id, work_type, target_id, approval_line_id, round, step_order,
                                                   approver_id, status, needs_reassign)
                        VALUES (?, ?::approval_work_type, ?, ?, ?, ?, ?, ?::approval_step_status, ?)
                        """,
                companyId, workType.name(), targetId, lineId, round, stepOrder, approverId, status.name(), needsReassign);
    }

    private int nextRound(long companyId, ApprovalWorkType workType, long targetId) {
        Integer max = jdbc.queryForObject("""
                        SELECT max(round) FROM approval_step
                        WHERE company_id = ? AND work_type = ?::approval_work_type AND target_id = ?
                        """,
                Integer.class, companyId, workType.name(), targetId);
        return max == null ? 1 : max + 1;
    }

    /** 단계를 잠그고 내 차례인지 확인. 다른 회사 단계는 404, 남의 단계·아직 차례 아님은 403, 이미 처리됨은 409. */
    private Step lockMyTurn(LoginUser user, long stepId) {
        Step step = lock(user.companyId(), stepId);
        if (step.approverId() == null || step.approverId() != user.employeeId()) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_MY_TURN);
        }
        return switch (step.status()) {
            case PENDING -> step;
            case WAITING -> throw new BusinessException(ErrorCode.APPROVAL_NOT_MY_TURN);
            default -> throw new BusinessException(ErrorCode.APPROVAL_ALREADY_DONE);
        };
    }

    private Step lock(long companyId, long stepId) {
        return jdbc.query(RAW_STEP_SELECT + " WHERE s.id = ? AND s.company_id = ? FOR UPDATE",
                        RAW_STEP_MAPPER, stepId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * 연장근무만 단계마다 인정 시간을 줄일 수 있다(앞 단계 값 이하, 첫 단계는 신청 시간 이하). 비우면 상한 그대로.
     * 다른 업무는 값을 받지 않는다.
     */
    private Integer approvedMinutes(long companyId, Step step, Integer requested) {
        if (step.workType() != ApprovalWorkType.OVERTIME) {
            if (requested != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "인정 시간은 연장근무 승인에만 입력합니다");
            }
            return null;
        }
        Integer cap = jdbc.query("""
                        SELECT approved_minutes FROM approval_step
                        WHERE company_id = ? AND work_type = 'OVERTIME' AND target_id = ? AND round = ?
                          AND status = 'APPROVED' AND step_order < ?
                        ORDER BY step_order DESC LIMIT 1
                        """,
                (rs, i) -> rs.getInt("approved_minutes"),
                companyId, step.targetId(), step.round(), step.stepOrder()).stream().findFirst()
                .orElseGet(() -> targets.get(ApprovalWorkType.OVERTIME).summary(step.targetId()).requestedMinutes());
        if (requested == null) {
            return cap;
        }
        if (requested < 0 || (cap != null && requested > cap)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "인정 시간은 0분 이상, 앞 단계 값 이하여야 합니다",
                    Map.of("maxMinutes", cap == null ? 0 : cap));
        }
        return requested;
    }

    private ApprovalStepView view(LoginUser user, long stepId) {
        return jdbc.queryForObject(STEP_SELECT + " WHERE s.id = ? AND s.company_id = ?",
                stepMapper(user.employeeId()), stepId, user.companyId());
    }

    /** 목록 행마다의 신청 요약. 업무 종류마다 summaries 를 한 번 부른다(역할 분담 2.1, 10/10 합의 ⑤). */
    private Map<Step, TargetSummary> summaries(List<Step> steps) {
        Map<ApprovalWorkType, Map<Long, TargetSummary>> byType = new EnumMap<>(ApprovalWorkType.class);
        steps.stream().collect(Collectors.groupingBy(Step::workType,
                        Collectors.mapping(Step::targetId, Collectors.toCollection(LinkedHashSet::new))))
                .forEach((type, ids) -> byType.put(type, targets.get(type).summaries(ids)));
        Map<Step, TargetSummary> result = new HashMap<>();
        for (Step s : steps) {
            result.put(s, byType.get(s.workType()).get(s.targetId()));
        }
        return result;
    }

    private Map<Long, Applicant> applicants(long companyId, Collection<Long> employeeIds) {
        Map<Long, Applicant> result = new HashMap<>();
        if (employeeIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        SELECT e.id, e.name, o.name AS org_unit_name FROM employee e
                        JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                        WHERE e.id = ANY(?) AND e.company_id = ?
                        """,
                rs -> {
                    result.put(rs.getLong("id"),
                            new Applicant(rs.getLong("id"), rs.getString("name"), rs.getString("org_unit_name")));
                },
                employeeIds.stream().distinct().toArray(Long[]::new), companyId);
        return result;
    }

    /** 단계 ID → 화면용 단계. */
    private Map<Long, ApprovalStepView> views(LoginUser user, List<Step> steps) {
        Map<Long, ApprovalStepView> result = new HashMap<>();
        if (steps.isEmpty()) {
            return result;
        }
        for (ApprovalStepView v : jdbc.query(STEP_SELECT + " WHERE s.id = ANY(?) AND s.company_id = ?",
                stepMapper(user.employeeId()), steps.stream().map(Step::id).toArray(Long[]::new), user.companyId())) {
            result.put(v.stepId(), v);
        }
        return result;
    }

    /** 각 단계가 속한 회차의 전체 단계(순서대로). */
    private Map<RoundKey, List<ApprovalStepView>> rounds(LoginUser user, List<Step> steps) {
        Map<RoundKey, List<ApprovalStepView>> result = new HashMap<>();
        if (steps.isEmpty()) {
            return result;
        }
        RowMapper<ApprovalStepView> mapper = stepMapper(user.employeeId());
        jdbc.query("""
                        SELECT s.work_type::text AS work_type, s.target_id, s.id, s.round, s.step_order, s.approver_id,
                               e.name AS approver_name, s.status::text AS status, s.approved_minutes, s.comment, s.acted_at,
                        """ + DISPLAY_ROUND + """

                        FROM approval_step s
                        JOIN unnest(?::text[], ?::bigint[], ?::int[]) AS k(work_type, target_id, round)
                          ON s.work_type::text = k.work_type AND s.target_id = k.target_id AND s.round = k.round
                        LEFT JOIN employee e ON e.id = s.approver_id AND e.company_id = s.company_id
                        WHERE s.company_id = ?
                        ORDER BY s.step_order
                        """,
                rs -> {
                    RoundKey key = new RoundKey(ApprovalWorkType.valueOf(rs.getString("work_type")),
                            rs.getLong("target_id"), rs.getInt("round"));
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(mapper.mapRow(rs, 0));
                },
                steps.stream().map(s -> s.workType().name()).toArray(String[]::new),
                steps.stream().map(Step::targetId).toArray(Long[]::new),
                steps.stream().map(Step::round).toArray(Integer[]::new),
                user.companyId());
        return result;
    }

    /**
     * 응답의 round. 휴가 취소 단계(LEAVE_CANCEL)는 DB에 취소 단계끼리 1부터 저장하고, 응답에서만 휴가의 마지막 round 를 더한다
     * (ERD 5.7, API 9장 approvalSteps — 신청 상세 · 승인 · 반려 응답 · 승인함이 같은 값).
     */
    private static final String DISPLAY_ROUND = """
            s.round + CASE WHEN s.work_type = 'LEAVE_CANCEL'
                           THEN COALESCE((SELECT max(l.round) FROM approval_step l
                                          WHERE l.company_id = s.company_id AND l.work_type = 'LEAVE'
                                            AND l.target_id = s.target_id), 0)
                           ELSE 0 END AS display_round""";

    private static final String STEP_SELECT = """
            SELECT s.id, s.step_order, s.approver_id, e.name AS approver_name, s.status::text AS status,
                   s.approved_minutes, s.comment, s.acted_at,
            """ + DISPLAY_ROUND + """

            FROM approval_step s
            LEFT JOIN employee e ON e.id = s.approver_id AND e.company_id = s.company_id
            """;

    private static RowMapper<ApprovalStepView> stepMapper(long viewerId) {
        return (rs, i) -> {
            Long approverId = rs.getObject("approver_id", Long.class);
            ApprovalStepStatus status = ApprovalStepStatus.valueOf(rs.getString("status"));
            return new ApprovalStepView(rs.getLong("id"), rs.getInt("display_round"), rs.getInt("step_order"), approverId,
                    rs.getString("approver_name"), status, rs.getObject("approved_minutes", Integer.class),
                    rs.getString("comment"), rs.getObject("acted_at", OffsetDateTime.class),
                    status == ApprovalStepStatus.PENDING && approverId != null && approverId == viewerId);
        };
    }

    private static final String RAW_STEP_SELECT = """
            SELECT s.id, s.work_type::text AS work_type, s.target_id, s.round, s.step_order, s.approver_id,
                   s.status::text AS status
            FROM approval_step s
            """;

    private static final RowMapper<Step> RAW_STEP_MAPPER = (rs, i) -> new Step(rs.getLong("id"),
            ApprovalWorkType.valueOf(rs.getString("work_type")), rs.getLong("target_id"), rs.getInt("round"),
            rs.getInt("step_order"), rs.getObject("approver_id", Long.class),
            ApprovalStepStatus.valueOf(rs.getString("status")));

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private record Step(long id, ApprovalWorkType workType, long targetId, int round, int stepOrder, Long approverId,
                        ApprovalStepStatus status) {
    }

    private record Applicant(long id, String name, String orgUnitName) {
    }

    private record RoundKey(ApprovalWorkType workType, long targetId, int round) {
        static RoundKey of(Step s) {
            return new RoundKey(s.workType(), s.targetId(), s.round());
        }
    }
}
