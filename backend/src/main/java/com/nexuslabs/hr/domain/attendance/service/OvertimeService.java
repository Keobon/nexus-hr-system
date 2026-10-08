package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.attendance.dto.OvertimeCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.OvertimeResponse;
import com.nexuslabs.hr.domain.attendance.dto.OvertimeRow;
import com.nexuslabs.hr.domain.attendance.entity.OvertimeRequest;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.repository.OvertimeRequestRepository;
import com.nexuslabs.hr.domain.employee.entity.Employee;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 연장근무 신청(F-ATT-06, BR-ATT-004). 승인은 승인 엔진이 하고, 확정·반려는 {@link OvertimeApprovalTarget}이 한다.
 * 등록·수정은 JPA, 상세 조회는 이름·소속을 함께 읽으려고 JDBC — 그래서 JPA로 바꾼 뒤 읽기 전에 flush 한다(백엔드 안내 6.1).
 */
@Service
public class OvertimeService {

    static final String RESIGNATION_REASON = "퇴직";

    private final OvertimeRequestRepository repository;
    private final ApprovalService approvalService;
    private final ScopeResolver scopeResolver;
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public OvertimeService(OvertimeRequestRepository repository, ApprovalService approvalService,
                           ScopeResolver scopeResolver, JdbcTemplate jdbc, EntityManager em) {
        this.repository = repository;
        this.approvalService = approvalService;
        this.scopeResolver = scopeResolver;
        this.jdbc = jdbc;
        this.em = em;
    }

    /**
     * 신청 → 승인 단계 저장. 모든 단계가 생략되면 바로 승인된다(승인 시간 = 신청 시간).
     * 지난 날짜도 신청할 수 있다(사후 신청).
     */
    @Transactional
    public OvertimeResponse create(LoginUser user, OvertimeCreateRequest request) {
        long cid = user.companyId();
        requireActive(cid, user.employeeId());
        LocalDate workDate = request.workDate();
        if (settled(cid, YearMonth.from(workDate))) {
            throw new BusinessException(ErrorCode.PAY_MONTH_SETTLED);
        }
        if (onLeaveOrTrip(cid, user.employeeId(), workDate)) {
            throw new BusinessException(ErrorCode.ATT_ON_LEAVE_OR_TRIP, "그날은 휴가·출장일이라 연장근무를 신청할 수 없습니다");
        }
        if (hasActiveRequest(cid, user.employeeId(), workDate)) {
            throw new BusinessException(ErrorCode.OVERTIME_DUPLICATE);
        }

        OffsetDateTime start = at(workDate, LocalTime.parse(request.plannedStart()));
        OffsetDateTime end = at(workDate, LocalTime.parse(request.plannedEnd()));
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        int minutes = (int) Duration.between(start, end).toMinutes();
        // 동시에 두 번 보내면 부분 유일 인덱스(ux_overtime_active)가 막고 OVERTIME_DUPLICATE 로 응답한다
        OvertimeRequest overtime = repository.save(new OvertimeRequest(em.getReference(Employee.class, user.employeeId()),
                workDate, start, end, minutes, request.reason().trim()));
        approvalService.open(cid, ApprovalWorkType.OVERTIME, overtime.getId(), user.employeeId());
        repository.flush();
        return detail(cid, overtime.getId(), user.employeeId());
    }

    /** 신청자 · 그 건의 승인자 · ATTENDANCE_READ(범위 안)만 본다. */
    @Transactional(readOnly = true)
    public OvertimeResponse get(LoginUser user, long id) {
        OvertimeResponse overtime = detail(user.companyId(), id, user.employeeId());
        boolean approver = overtime.approvalSteps().stream()
                .anyMatch(s -> s.approverId() != null && s.approverId() == user.employeeId());
        if (overtime.employeeId() != user.employeeId() && !approver) {
            Scope scope = scopeResolver.findScope(user, PermissionCode.ATTENDANCE_READ)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            scope.assertContains(overtime.employeeId());
        }
        return overtime;
    }

    /** 승인대기 중에만 본인이 철회한다. 남은 승인 단계도 닫는다. 남의 신청은 404. */
    @Transactional
    public OvertimeResponse withdraw(LoginUser user, long id) {
        OvertimeRequest overtime = repository.findById(id)
                .filter(o -> o.getEmployee().getId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (overtime.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "승인대기 중인 신청만 철회할 수 있습니다");
        }
        overtime.cancel(null);
        approvalService.withdraw(user.companyId(), ApprovalWorkType.OVERTIME, id);
        repository.flush();
        return detail(user.companyId(), id, user.employeeId());
    }

    /** 내 신청 목록. month 를 비우면 전체. 최근 근무일부터. */
    @Transactional(readOnly = true)
    public PageImpl<OvertimeRow> mine(LoginUser user, YearMonth month, RequestStatus status, Pageable pageable) {
        return page(user.companyId(), " AND o.employee_id = ?", List.of(user.employeeId()), month, null, status,
                pageable);
    }

    /**
     * 다른 직원의 신청 목록(ATTENDANCE_READ). 팀 범위면 내 팀 직원 것만 — 범위 밖 조직을 필터로 넣어도 범위 안만 나온다.
     * orgUnitId 는 하위 조직까지 포함한다. month 를 비우면 전체.
     */
    @Transactional(readOnly = true)
    public PageImpl<OvertimeRow> list(LoginUser user, YearMonth month, Long orgUnitId, RequestStatus status,
                                      Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.ATTENDANCE_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return scope.all()
                ? page(user.companyId(), "", List.of(), month, orgUnitId, status, pageable)
                : page(user.companyId(), " AND o.employee_id = ANY(?)",
                        List.of((Object) scope.employeeIds().toArray(Long[]::new)), month, orgUnitId, status, pageable);
    }

    /**
     * 퇴직 처리(B-10)가 같은 트랜잭션에서 부른다(역할 분담 v2 2.1). 그 직원의 승인대기 신청을 취소(사유 "퇴직")하고
     * 남은 승인 단계를 닫는다. 취소한 건수를 돌려준다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelPendingByResignation(long companyId, long employeeId) {
        List<Long> ids = jdbc.queryForList("""
                        SELECT id FROM overtime_request
                        WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'
                        FOR UPDATE
                        """,
                Long.class, companyId, employeeId);
        for (long id : ids) {
            jdbc.update("""
                            UPDATE overtime_request SET status = 'CANCELLED', cancel_reason = ?, updated_at = now()
                            WHERE id = ? AND company_id = ?
                            """,
                    RESIGNATION_REASON, id, companyId);
            approvalService.withdraw(companyId, ApprovalWorkType.OVERTIME, id);
        }
        return ids.size();
    }

    /** 홈 대시보드 me.myPending.overtime(역할 분담 v2 2.1) — 그 직원이 신청한 승인대기 건수. */
    @Transactional(readOnly = true)
    public long countPending(long companyId, long employeeId) {
        return jdbc.queryForObject("""
                        SELECT count(*) FROM overtime_request
                        WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'
                        """,
                Long.class, companyId, employeeId);
    }

    // ------------------------------------------------------------------

    private PageImpl<OvertimeRow> page(long companyId, String employeeFilter, List<Object> employeeArgs,
                                       YearMonth month, Long orgUnitId, RequestStatus status, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE o.company_id = ?").append(employeeFilter);
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.addAll(employeeArgs);
        if (month != null) {
            where.append(" AND o.work_date BETWEEN ? AND ?");
            args.add(Date.valueOf(month.atDay(1)));
            args.add(Date.valueOf(month.atEndOfMonth()));
        }
        if (status != null) {
            where.append(" AND o.status = ?::request_status");
            args.add(status.name());
        }
        if (orgUnitId != null) {
            where.append("""
                     AND e.org_unit_id IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT c.id FROM org_unit c JOIN sub s ON c.parent_id = s.id WHERE c.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(orgUnitId, companyId, companyId));
        }
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());
        args.addAll(List.of(pageable.getPageSize(), pageable.getOffset()));
        List<OvertimeRow> rows = jdbc.query(ROW_SELECT + FROM + where
                        + " ORDER BY o.work_date DESC, o.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new OvertimeRow(rs.getLong("id"), rs.getLong("employee_id"), rs.getString("employee_name"),
                        rs.getString("org_unit_name"), rs.getObject("work_date", LocalDate.class),
                        seoul(rs.getObject("planned_start", OffsetDateTime.class)),
                        seoul(rs.getObject("planned_end", OffsetDateTime.class)), rs.getInt("requested_minutes"),
                        rs.getObject("approved_minutes", Integer.class), rs.getString("reason"),
                        RequestStatus.valueOf(rs.getString("status")),
                        seoul(rs.getObject("created_at", OffsetDateTime.class)), null),
                args.toArray());
        Map<Long, CurrentStep> steps = approvalService.currentSteps(companyId, ApprovalWorkType.OVERTIME,
                rows.stream().map(OvertimeRow::id).toList());
        return new PageImpl<>(rows.stream().map(r -> r.withCurrentStep(steps.get(r.id()))).toList(), pageable, total);
    }

    private static final String ROW_SELECT = """
            SELECT o.id, o.employee_id, e.name AS employee_name, u.name AS org_unit_name, o.work_date,
                   o.planned_start, o.planned_end, o.requested_minutes, o.approved_minutes, o.reason,
                   o.status::text AS status, o.created_at
            """;

    private static final String FROM = """
             FROM overtime_request o
             JOIN employee e ON e.id = o.employee_id AND e.company_id = o.company_id
             JOIN org_unit u ON u.id = e.org_unit_id AND u.company_id = e.company_id
            """;

    private OvertimeResponse detail(long companyId, long id, long viewerId) {
        List<ApprovalStepView> steps = approvalService.steps(companyId, ApprovalWorkType.OVERTIME, id, viewerId);
        return jdbc.query("""
                                SELECT o.id, o.employee_id, e.name AS employee_name, u.name AS org_unit_name, o.work_date,
                                       o.planned_start, o.planned_end, o.requested_minutes, o.approved_minutes, o.reason,
                                       o.status::text AS status, o.cancel_reason, o.created_at
                                FROM overtime_request o
                                JOIN employee e ON e.id = o.employee_id AND e.company_id = o.company_id
                                JOIN org_unit u ON u.id = e.org_unit_id AND u.company_id = e.company_id
                                WHERE o.id = ? AND o.company_id = ?
                                """,
                        (rs, i) -> new OvertimeResponse(rs.getLong("id"), rs.getLong("employee_id"),
                                rs.getString("employee_name"), rs.getString("org_unit_name"),
                                rs.getObject("work_date", LocalDate.class),
                                seoul(rs.getObject("planned_start", OffsetDateTime.class)),
                                seoul(rs.getObject("planned_end", OffsetDateTime.class)),
                                rs.getInt("requested_minutes"), rs.getObject("approved_minutes", Integer.class),
                                rs.getString("reason"), RequestStatus.valueOf(rs.getString("status")),
                                rs.getString("cancel_reason"), seoul(rs.getObject("created_at", OffsetDateTime.class)),
                                steps),
                        id, companyId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 재직중만 신청한다(API 설계서 1.7). 퇴직자는 로그인 단계에서 막힌다. */
    private void requireActive(long companyId, long employeeId) {
        Optional<String> status = jdbc.queryForList("SELECT status::text FROM employee WHERE id = ? AND company_id = ?",
                String.class, employeeId, companyId).stream().findFirst();
        if (!"ACTIVE".equals(status.orElse(null))) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_ACTIVE);
        }
    }

    private boolean settled(long companyId, YearMonth month) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payroll_run WHERE company_id = ? AND pay_month = ?)",
                Boolean.class, companyId, month.toString()));
    }

    /** 그날 근태가 휴가·출장이면 거부한다(2.3 24번) — 휴가 기간 안 주말·휴일, 출근 기록이 남은 충돌일은 신청할 수 있다. */
    private boolean onLeaveOrTrip(long companyId, long employeeId, LocalDate workDate) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM attendance
                                       WHERE company_id = ? AND employee_id = ? AND work_date = ?
                                         AND status IN ('ON_VACATION', 'ON_BUSINESS_TRIP'))
                        """,
                Boolean.class, companyId, employeeId, Date.valueOf(workDate)));
    }

    private boolean hasActiveRequest(long companyId, long employeeId, LocalDate workDate) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM overtime_request
                                       WHERE company_id = ? AND employee_id = ? AND work_date = ?
                                         AND status IN ('PENDING', 'APPROVED'))
                        """,
                Boolean.class, companyId, employeeId, Date.valueOf(workDate)));
    }

    private static OffsetDateTime at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ClockConfig.ZONE).toOffsetDateTime();
    }

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }
}
