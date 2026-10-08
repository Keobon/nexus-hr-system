package com.nexuslabs.hr.domain.leave.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalPlan;
import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.leave.dto.LeaveBalance;
import com.nexuslabs.hr.domain.leave.dto.LeavePreview;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestCreate;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestResponse;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestRow;
import com.nexuslabs.hr.domain.leave.entity.LeaveRequest;
import com.nexuslabs.hr.domain.leave.entity.LeaveStatus;
import com.nexuslabs.hr.domain.leave.entity.LeaveType;
import com.nexuslabs.hr.domain.leave.repository.LeaveRequestRepository;
import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 휴가 신청 · 미리보기 · 철회 · 취소 요청 · 조회(F-LEAVE-03·04·06). 승인 확정 · 반려는 {@link LeaveApprovalTarget},
 * 취소 승인 · 반려는 {@link LeaveCancelApprovalTarget}이 한다.
 * 신청 저장과 상태 변경은 JPA, 목록 · 상세 · 잔여 · 겹침 검사는 이름·소속·승인 단계를 함께 읽으려고 JDBC(백엔드 안내 5장).
 */
@Service
public class LeaveRequestService {

    /** 퇴직 자동 취소의 취소 사유 — 연장근무 · 출장 · 경비와 같은 값. */
    static final String RESIGNATION_REASON = "퇴직";

    private final LeaveRequestRepository repository;
    private final LeaveCalculator calculator;
    private final WorkCalendar workCalendar;
    private final ApprovalService approvalService;
    private final ScopeResolver scopeResolver;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public LeaveRequestService(LeaveRequestRepository repository, LeaveCalculator calculator, WorkCalendar workCalendar,
                               ApprovalService approvalService, ScopeResolver scopeResolver, JdbcTemplate jdbc,
                               EntityManager em, Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.workCalendar = workCalendar;
        this.approvalService = approvalService;
        this.scopeResolver = scopeResolver;
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    /** 신청 전 미리보기 — 실제 신청과 같은 {@link #evaluate}를 쓰고 저장하지 않는다(API 설계서 8장). */
    @Transactional(readOnly = true)
    public LeavePreview preview(LoginUser user, long leaveTypeId, LocalDate startDate, LocalDate endDate) {
        Evaluation ev = evaluate(user.companyId(), user.employeeId(), leaveTypeId, startDate, endDate);
        LeavePreview.Balance balance = ev.balance() == null ? null : new LeavePreview.Balance(
                ev.balance().leaveTypeName(), ev.balance().granted(), ev.balance().used(), ev.balance().pending(),
                ev.balance().remaining(), ev.balance().remaining() - ev.days());
        ApprovalPlan plan = ev.plan();
        return new LeavePreview(ev.days(), ev.leaveYear(), balance,
                plan == null ? null : new LeavePreview.ApprovalLine(plan.approvalLineId(), plan.approvalLineName()),
                plan == null ? List.of() : plan.steps().stream()
                        .map(s -> new LeavePreview.Step(s.stepOrder(), s.approverId(), s.approverName(), s.skipped()))
                        .toList(),
                ev.errors().stream().map(e -> e.code().name()).toList());
    }

    /**
     * 신청 → 승인 단계 저장. 모든 단계가 생략되면 즉시 승인 + 근태 반영(F-LEAVE-03).
     * 겹침 · 잔여 검사 전에 직원 행을 잠가 같은 직원의 휴가 · 출장 신청을 한 줄로 세운다(역할 분담 v2 2.1 J2).
     */
    @Transactional
    public LeaveRequestResponse create(LoginUser user, LeaveRequestCreate request) {
        long cid = user.companyId();
        jdbc.queryForList("SELECT id FROM employee WHERE id = ? AND company_id = ? FOR UPDATE",
                Long.class, user.employeeId(), cid);
        Evaluation ev = evaluate(cid, user.employeeId(), request.leaveTypeId(), request.startDate(), request.endDate());
        if (!ev.errors().isEmpty()) {
            throw ev.errors().getFirst();
        }
        LeaveRequest leave = repository.save(new LeaveRequest(em.getReference(Employee.class, user.employeeId()),
                em.getReference(LeaveType.class, request.leaveTypeId()), (short) ev.leaveYear(), request.startDate(),
                request.endDate(), (short) ev.days(), blankToNull(request.reason())));
        approvalService.open(cid, ApprovalWorkType.LEAVE, leave.getId(), user.employeeId());
        repository.flush();
        return detail(cid, leave.getId(), user.employeeId());
    }

    /** 신청자 · 그 건(취소 요청 포함)의 승인자 · LEAVE_READ(범위 안)만 본다. */
    @Transactional(readOnly = true)
    public LeaveRequestResponse get(LoginUser user, long id) {
        LeaveRequestResponse leave = detail(user.companyId(), id, user.employeeId());
        if (leave.employeeId() == user.employeeId() || leave.approvalSteps().stream()
                .anyMatch(s -> s.approverId() != null && s.approverId() == user.employeeId())) {
            return leave;
        }
        Scope scope = scopeResolver.findScope(user, PermissionCode.LEAVE_READ)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        scope.assertContains(leave.employeeId());
        return leave;
    }

    /** 승인대기 중에만 본인이 철회한다 → 취소완료, 남은 단계 취소(F-LEAVE-06). 남의 휴가는 404. */
    @Transactional
    public LeaveRequestResponse withdraw(LoginUser user, long id) {
        LeaveRequest leave = mine(user, id);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "승인대기 중인 휴가만 철회할 수 있습니다");
        }
        leave.withdraw(null);
        approvalService.withdraw(user.companyId(), ApprovalWorkType.LEAVE, id);
        repository.flush();
        return detail(user.companyId(), id, user.employeeId());
    }

    /**
     * 승인된 휴가의 취소 요청 — 시작일 전까지만(BR-LEAVE-004). 원래 마지막 승인자에게 취소 승인 단계 1개,
     * 자동 승인이던 휴가면 바로 취소완료 + 휴가 근태 삭제({@link LeaveCancelApprovalTarget}).
     */
    @Transactional
    public LeaveRequestResponse requestCancel(LoginUser user, long id, String reason) {
        LeaveRequest leave = mine(user, id);
        if (leave.getStatus() != LeaveStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "승인완료된 휴가만 취소 요청할 수 있습니다");
        }
        if (!LocalDate.now(clock).isBefore(leave.getStartDate())) {
            throw new BusinessException(ErrorCode.LEAVE_ALREADY_STARTED);
        }
        leave.requestCancel(blankToNull(reason));
        repository.flush();
        approvalService.openLeaveCancel(user.companyId(), id, user.employeeId());
        repository.flush();
        return detail(user.companyId(), id, user.employeeId());
    }

    /** 내 휴가 목록. leaveYear · status 는 선택. 최근 시작일부터. */
    @Transactional(readOnly = true)
    public PageImpl<LeaveRequestRow> mine(LoginUser user, Integer leaveYear, LeaveStatus status, Pageable pageable) {
        Filter filter = new Filter(null, null, null, status, null, leaveYear);
        return page(user.companyId(), " AND r.employee_id = ?", List.of(user.employeeId()), filter, pageable);
    }

    /**
     * 다른 직원의 휴가 목록(LEAVE_READ). 팀 범위면 내 팀 직원 것만. from · to 는 휴가 기간이 그 사이에 걸치는 것,
     * orgUnitId 는 하위 조직까지 포함한다.
     */
    @Transactional(readOnly = true)
    public PageImpl<LeaveRequestRow> list(LoginUser user, LocalDate from, LocalDate to, Long orgUnitId,
                                         LeaveStatus status, Long leaveTypeId, Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.LEAVE_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Filter filter = new Filter(from, to, orgUnitId, status, leaveTypeId, null);
        return scope.all()
                ? page(user.companyId(), "", List.of(), filter, pageable)
                : page(user.companyId(), " AND r.employee_id = ANY(?)",
                        List.of((Object) scope.employeeIds().toArray(Long[]::new)), filter, pageable);
    }

    /**
     * 퇴직 처리(B-10)가 같은 트랜잭션에서 부른다(역할 분담 v2 2.1 J3). 승인대기 휴가를 취소(사유 "퇴직")하고 단계를 닫는다.
     * 취소요청 중인 휴가는 승인된 휴가라 건드리지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelPendingByResignation(long companyId, long employeeId) {
        List<Long> ids = jdbc.queryForList("""
                        SELECT id FROM leave_request
                        WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'
                        FOR UPDATE
                        """,
                Long.class, companyId, employeeId);
        for (long id : ids) {
            jdbc.update("""
                            UPDATE leave_request SET status = 'CANCELLED', cancel_reason = ?, updated_at = now()
                            WHERE id = ? AND company_id = ?
                            """,
                    RESIGNATION_REASON, id, companyId);
            approvalService.withdraw(companyId, ApprovalWorkType.LEAVE, id);
        }
        return ids.size();
    }

    /** 홈 대시보드 me.myPending.leave(역할 분담 v2 2.1 J4) — 그 직원이 신청한 승인대기 건수. */
    @Transactional(readOnly = true)
    public long countPending(long companyId, long employeeId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM leave_request WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'",
                Long.class, companyId, employeeId);
    }

    // ------------------------------------------------------------------

    /**
     * 신청 검사 한 벌 — 미리보기와 신청이 같이 쓴다. 잘못된 입력(시작일 > 종료일 · 없는 종류 · 비활성 종류)은 바로 던지고,
     * 신청을 막는 업무 오류는 모아서 돌려준다. 순서: 재직 · 연도 · 0일 · 겹침 · 잔여 · 승인자.
     */
    private Evaluation evaluate(long cid, long employeeId, long leaveTypeId, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw BusinessException.invalidFields(Map.of("endDate", "종료일은 시작일 이후여야 합니다"));
        }
        TypeInfo type = jdbc.query("SELECT deducts_balance, is_active FROM leave_type WHERE id = ? AND company_id = ?",
                        (rs, i) -> new TypeInfo(rs.getBoolean("deducts_balance"), rs.getBoolean("is_active")),
                        leaveTypeId, cid).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "휴가 종류를 찾을 수 없습니다"));
        if (!type.active()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE, "사용 중지된 휴가 종류입니다");
        }

        List<BusinessException> errors = new ArrayList<>();
        String status = jdbc.queryForObject("SELECT status::text FROM employee WHERE id = ? AND company_id = ?",
                String.class, employeeId, cid);
        if (!"ACTIVE".equals(status)) {
            errors.add(new BusinessException(ErrorCode.EMPLOYEE_NOT_ACTIVE));
        }
        int leaveYear = calculator.leaveYearContaining(cid, start).year();
        if (calculator.leaveYearContaining(cid, end).year() != leaveYear) {
            errors.add(new BusinessException(ErrorCode.LEAVE_CROSS_YEAR));
        }
        int days = workCalendar.workdaysBetween(cid, start, end);
        if (days == 0) {
            errors.add(new BusinessException(ErrorCode.LEAVE_ZERO_DAYS));
        }
        overlap(cid, employeeId, start, end).ifPresent(errors::add);
        LeaveBalance balance = null;
        if (type.deductsBalance()) {
            balance = calculator.balance(cid, employeeId, leaveTypeId, leaveYear);
            if (balance.remaining() < days) {
                errors.add(new BusinessException(ErrorCode.LEAVE_INSUFFICIENT_BALANCE,
                        Map.of("remaining", balance.remaining(), "requested", days)));
            }
        }
        ApprovalPlan plan = null;
        try {
            plan = approvalService.preview(cid, ApprovalWorkType.LEAVE, employeeId);
        } catch (BusinessException e) {
            if (e.code() != ErrorCode.APPROVER_NOT_FOUND) {
                throw e;
            }
            errors.add(e);
        }
        return new Evaluation(days, leaveYear, balance, plan, errors);
    }

    /** 휴가는 승인대기 · 승인완료 · 취소요청, 출장은 승인대기 · 승인완료를 겹침으로 본다(BR-LEAVE-008, BR-ATT-005). */
    private Optional<BusinessException> overlap(long cid, long employeeId, LocalDate start, LocalDate end) {
        List<Long> leave = jdbc.queryForList("""
                        SELECT id FROM leave_request
                        WHERE company_id = ? AND employee_id = ? AND status IN ('PENDING', 'APPROVED', 'CANCEL_REQUESTED')
                          AND start_date <= ? AND end_date >= ?
                        ORDER BY start_date LIMIT 1
                        """,
                Long.class, cid, employeeId, Date.valueOf(end), Date.valueOf(start));
        if (!leave.isEmpty()) {
            return Optional.of(overlapError("LEAVE", leave.getFirst()));
        }
        List<Long> trip = jdbc.queryForList("""
                        SELECT id FROM business_trip
                        WHERE company_id = ? AND employee_id = ? AND status IN ('PENDING', 'APPROVED')
                          AND start_date <= ? AND end_date >= ?
                        ORDER BY start_date LIMIT 1
                        """,
                Long.class, cid, employeeId, Date.valueOf(end), Date.valueOf(start));
        return trip.isEmpty()
                ? Optional.empty()
                : Optional.of(overlapError("BUSINESS_TRIP", trip.getFirst()));
    }

    private static BusinessException overlapError(String conflictType, long conflictId) {
        return new BusinessException(ErrorCode.PERIOD_OVERLAP,
                Map.of("conflictType", conflictType, "conflictId", conflictId));
    }

    private LeaveRequest mine(LoginUser user, long id) {
        return repository.findById(id)
                .filter(r -> r.getEmployee().getId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 신청 단계 뒤에 취소 요청 단계(LEAVE_CANCEL)를 잇는다. 취소 단계의 round 는 신청의 마지막 round 다음부터. */
    private LeaveRequestResponse detail(long cid, long id, long viewerId) {
        List<ApprovalStepView> leaveSteps = approvalService.steps(cid, ApprovalWorkType.LEAVE, id, viewerId);
        int lastRound = leaveSteps.stream().mapToInt(ApprovalStepView::round).max().orElse(0);
        List<ApprovalStepView> steps = Stream.concat(leaveSteps.stream(),
                approvalService.steps(cid, ApprovalWorkType.LEAVE_CANCEL, id, viewerId).stream()
                        .map(s -> new ApprovalStepView(s.stepId(), lastRound + s.round(), s.stepOrder(), s.approverId(),
                                s.approverName(), s.status(), s.approvedMinutes(), s.comment(), s.actedAt(),
                                s.isMyTurn()))).toList();
        return jdbc.query(SELECT + ", r.cancel_reason" + FROM + " WHERE r.id = ? AND r.company_id = ?",
                        (rs, i) -> new LeaveRequestResponse(rs.getLong("id"), rs.getLong("employee_id"),
                                rs.getString("employee_name"), rs.getString("org_unit_name"),
                                rs.getLong("leave_type_id"), rs.getString("leave_type_name"), rs.getInt("leave_year"),
                                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                                rs.getInt("days"), LeaveStatus.valueOf(rs.getString("status")), rs.getString("reason"),
                                rs.getString("cancel_reason"), seoul(rs.getObject("created_at", OffsetDateTime.class)),
                                steps),
                        id, cid)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private PageImpl<LeaveRequestRow> page(long cid, String employeeFilter, List<Object> employeeArgs, Filter f,
                                           Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE r.company_id = ?").append(employeeFilter);
        List<Object> args = new ArrayList<>();
        args.add(cid);
        args.addAll(employeeArgs);
        if (f.from() != null) {
            where.append(" AND r.end_date >= ?");
            args.add(Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            where.append(" AND r.start_date <= ?");
            args.add(Date.valueOf(f.to()));
        }
        if (f.status() != null) {
            where.append(" AND r.status = ?::leave_status");
            args.add(f.status().name());
        }
        if (f.leaveTypeId() != null) {
            where.append(" AND r.leave_type_id = ?");
            args.add(f.leaveTypeId());
        }
        if (f.leaveYear() != null) {
            where.append(" AND r.leave_year = ?");
            args.add(f.leaveYear());
        }
        if (f.orgUnitId() != null) {
            where.append("""
                     AND e.org_unit_id IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT c.id FROM org_unit c JOIN sub s ON c.parent_id = s.id WHERE c.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(f.orgUnitId(), cid, cid));
        }
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());
        args.addAll(List.of(pageable.getPageSize(), pageable.getOffset()));
        List<LeaveRequestRow> rows = jdbc.query(SELECT + FROM + where
                        + " ORDER BY r.start_date DESC, r.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new LeaveRequestRow(rs.getLong("id"), rs.getLong("employee_id"),
                        rs.getString("employee_name"), rs.getString("org_unit_name"), rs.getLong("leave_type_id"),
                        rs.getString("leave_type_name"), rs.getInt("leave_year"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                        rs.getInt("days"), LeaveStatus.valueOf(rs.getString("status")), rs.getString("reason"),
                        seoul(rs.getObject("created_at", OffsetDateTime.class)), null),
                args.toArray());
        Map<Long, CurrentStep> steps = approvalService.currentSteps(cid, ApprovalWorkType.LEAVE,
                rows.stream().map(LeaveRequestRow::id).toList());
        return new PageImpl<>(rows.stream().map(r -> r.withCurrentStep(steps.get(r.id()))).toList(), pageable, total);
    }

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static final String SELECT = """
            SELECT r.id, r.employee_id, e.name AS employee_name, u.name AS org_unit_name, r.leave_type_id,
                   t.name AS leave_type_name, r.leave_year, r.start_date, r.end_date, r.days, r.status::text AS status,
                   r.reason, r.created_at
            """;

    private static final String FROM = """
             FROM leave_request r
             JOIN employee e ON e.id = r.employee_id AND e.company_id = r.company_id
             JOIN org_unit u ON u.id = e.org_unit_id AND u.company_id = e.company_id
             JOIN leave_type t ON t.id = r.leave_type_id AND t.company_id = r.company_id
            """;

    private record TypeInfo(boolean deductsBalance, boolean active) {
    }

    private record Filter(LocalDate from, LocalDate to, Long orgUnitId, LeaveStatus status, Long leaveTypeId,
                          Integer leaveYear) {
    }

    private record Evaluation(int days, int leaveYear, LeaveBalance balance, ApprovalPlan plan,
                              List<BusinessException> errors) {
    }
}
