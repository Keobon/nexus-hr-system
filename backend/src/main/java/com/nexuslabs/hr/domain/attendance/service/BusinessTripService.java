package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripResponse;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripRow;
import com.nexuslabs.hr.domain.attendance.entity.BusinessTrip;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.entity.TripType;
import com.nexuslabs.hr.domain.attendance.repository.BusinessTripRepository;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.auth.LoginUser;
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

/**
 * 출장 신청 · 결과 보고(F-ATT-07, BR-ATT-005). 승인은 승인 엔진이 하고, 확정(근태 생성) · 반려는
 * {@link BusinessTripApprovalTarget}이 한다. 등록·수정은 JPA, 조회는 이름·소속·경비 청구를 함께 읽으려고 JDBC.
 */
@Service
public class BusinessTripService {

    private final BusinessTripRepository repository;
    private final ApprovalService approvalService;
    private final ScopeResolver scopeResolver;
    private final RequestAccess access;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public BusinessTripService(BusinessTripRepository repository, ApprovalService approvalService,
                               ScopeResolver scopeResolver, RequestAccess access, JdbcTemplate jdbc, EntityManager em,
                               Clock clock) {
        this.repository = repository;
        this.approvalService = approvalService;
        this.scopeResolver = scopeResolver;
        this.access = access;
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    /**
     * 신청 → 승인 단계 저장. 내 승인대기·승인 휴가나 출장과 날짜가 겹치면 PERIOD_OVERLAP(details.conflictType · conflictId).
     * 겹침 검사 전에 직원 행을 잠가 휴가 신청과 한 줄로 세운다(역할 분담 v2 2.1 J2). 지난 날짜도 신청할 수 있다.
     */
    @Transactional
    public BusinessTripResponse create(LoginUser user, BusinessTripCreateRequest request) {
        long cid = user.companyId();
        if (request.startDate().isAfter(request.endDate())) {
            throw BusinessException.invalidFields(Map.of("endDate", "종료일은 시작일 이후여야 합니다"));
        }
        jdbc.queryForList("SELECT id FROM employee WHERE id = ? AND company_id = ? FOR UPDATE",
                Long.class, user.employeeId(), cid);
        access.requireActive(cid, user.employeeId());
        checkOverlap(cid, user.employeeId(), request.startDate(), request.endDate());

        BusinessTrip trip = repository.save(new BusinessTrip(em.getReference(Employee.class, user.employeeId()),
                request.tripType(), request.destination().trim(), request.purpose().trim(), request.startDate(),
                request.endDate(), request.estimatedCost()));
        approvalService.open(cid, ApprovalWorkType.BUSINESS_TRIP, trip.getId(), user.employeeId());
        repository.flush();
        return detail(cid, trip.getId(), user.employeeId());
    }

    /** 신청자 · 그 건의 승인자 · ATTENDANCE_READ(범위 안)만 본다. */
    @Transactional(readOnly = true)
    public BusinessTripResponse get(LoginUser user, long id) {
        BusinessTripResponse trip = detail(user.companyId(), id, user.employeeId());
        access.checkViewer(user, trip.employeeId(), trip.approvalSteps());
        return trip;
    }

    /** 승인대기 중에만 본인이 철회한다. 승인된 출장의 취소는 없다(일정 변경은 근태 정정). 남의 출장은 404. */
    @Transactional
    public BusinessTripResponse withdraw(LoginUser user, long id) {
        BusinessTrip trip = mine(user, id);
        if (trip.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "승인대기 중인 출장만 철회할 수 있습니다");
        }
        trip.cancel(null);
        approvalService.withdraw(user.companyId(), ApprovalWorkType.BUSINESS_TRIP, id);
        repository.flush();
        return detail(user.companyId(), id, user.employeeId());
    }

    /** 결과 보고 작성·수정 — 승인완료이고 종료일 당일부터. 아니면 INVALID_STATE. */
    @Transactional
    public BusinessTripResponse report(LoginUser user, long id, String reportText) {
        BusinessTrip trip = mine(user, id);
        if (trip.getStatus() != RequestStatus.APPROVED || LocalDate.now(clock).isBefore(trip.getEndDate())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "승인된 출장의 종료일부터 결과 보고를 쓸 수 있습니다");
        }
        trip.writeReport(reportText.strip(), OffsetDateTime.now(clock));
        repository.flush();
        return detail(user.companyId(), id, user.employeeId());
    }

    /** 내 출장 목록. 최근 시작일부터. */
    @Transactional(readOnly = true)
    public PageImpl<BusinessTripRow> mine(LoginUser user, RequestStatus status, Pageable pageable) {
        return page(user.companyId(), " AND t.employee_id = ?", List.of(user.employeeId()), null, null, null, status,
                pageable);
    }

    /**
     * 다른 직원의 출장 목록(ATTENDANCE_READ). 팀 범위면 내 팀 직원 것만. from · to 는 출장 기간이 그 사이에 걸치는 것,
     * orgUnitId 는 하위 조직까지 포함한다.
     */
    @Transactional(readOnly = true)
    public PageImpl<BusinessTripRow> list(LoginUser user, LocalDate from, LocalDate to, Long orgUnitId,
                                          RequestStatus status, Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.ATTENDANCE_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return scope.all()
                ? page(user.companyId(), "", List.of(), from, to, orgUnitId, status, pageable)
                : page(user.companyId(), " AND t.employee_id = ANY(?)",
                        List.of((Object) scope.employeeIds().toArray(Long[]::new)), from, to, orgUnitId, status, pageable);
    }

    /** 퇴직 처리(B-10)가 같은 트랜잭션에서 부른다(역할 분담 v2 2.1). 승인대기 출장을 취소(사유 "퇴직")하고 단계를 닫는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelPendingByResignation(long companyId, long employeeId) {
        List<Long> ids = jdbc.queryForList("""
                        SELECT id FROM business_trip
                        WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'
                        FOR UPDATE
                        """,
                Long.class, companyId, employeeId);
        for (long id : ids) {
            jdbc.update("""
                            UPDATE business_trip SET status = 'CANCELLED', cancel_reason = ?, updated_at = now()
                            WHERE id = ? AND company_id = ?
                            """,
                    OvertimeService.RESIGNATION_REASON, id, companyId);
            approvalService.withdraw(companyId, ApprovalWorkType.BUSINESS_TRIP, id);
        }
        return ids.size();
    }

    /** 홈 대시보드 me.myPending.businessTrip(역할 분담 v2 2.1) — 그 직원이 신청한 승인대기 건수. */
    @Transactional(readOnly = true)
    public long countPending(long companyId, long employeeId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM business_trip WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'",
                Long.class, companyId, employeeId);
    }

    // ------------------------------------------------------------------

    private BusinessTrip mine(LoginUser user, long id) {
        return repository.findById(id)
                .filter(t -> t.getEmployee().getId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 휴가는 승인대기 · 승인완료 · 취소요청, 출장은 승인대기 · 승인완료를 겹침으로 본다(BR-ATT-005). */
    private void checkOverlap(long companyId, long employeeId, LocalDate start, LocalDate end) {
        List<Long> leave = jdbc.queryForList("""
                        SELECT id FROM leave_request
                        WHERE company_id = ? AND employee_id = ? AND status IN ('PENDING', 'APPROVED', 'CANCEL_REQUESTED')
                          AND start_date <= ? AND end_date >= ?
                        ORDER BY start_date LIMIT 1
                        """,
                Long.class, companyId, employeeId, Date.valueOf(end), Date.valueOf(start));
        if (!leave.isEmpty()) {
            throw overlap("LEAVE", leave.getFirst());
        }
        List<Long> trip = jdbc.queryForList("""
                        SELECT id FROM business_trip
                        WHERE company_id = ? AND employee_id = ? AND status IN ('PENDING', 'APPROVED')
                          AND start_date <= ? AND end_date >= ?
                        ORDER BY start_date LIMIT 1
                        """,
                Long.class, companyId, employeeId, Date.valueOf(end), Date.valueOf(start));
        if (!trip.isEmpty()) {
            throw overlap("BUSINESS_TRIP", trip.getFirst());
        }
    }

    private static BusinessException overlap(String conflictType, long conflictId) {
        return new BusinessException(ErrorCode.PERIOD_OVERLAP,
                Map.of("conflictType", conflictType, "conflictId", conflictId));
    }

    private BusinessTripResponse detail(long companyId, long id, long viewerId) {
        List<ApprovalStepView> steps = approvalService.steps(companyId, ApprovalWorkType.BUSINESS_TRIP, id, viewerId);
        return jdbc.query(SELECT + FROM + " WHERE t.id = ? AND t.company_id = ?",
                        (rs, i) -> new BusinessTripResponse(rs.getLong("id"), rs.getLong("employee_id"),
                                rs.getString("employee_name"), rs.getString("org_unit_name"),
                                TripType.valueOf(rs.getString("trip_type")), rs.getString("destination"),
                                rs.getString("purpose"), rs.getObject("start_date", LocalDate.class),
                                rs.getObject("end_date", LocalDate.class), rs.getObject("estimated_cost", Long.class),
                                RequestStatus.valueOf(rs.getString("status")), rs.getString("cancel_reason"),
                                rs.getString("report_text"),
                                RequestAccess.seoul(rs.getObject("reported_at", OffsetDateTime.class)),
                                RequestAccess.seoul(rs.getObject("created_at", OffsetDateTime.class)),
                                rs.getObject("claim_id") == null ? null : new BusinessTripResponse.ExpenseClaimSummary(
                                        rs.getLong("claim_id"), RequestStatus.valueOf(rs.getString("claim_status")),
                                        rs.getLong("claim_total"), rs.getString("claim_pay_month")),
                                steps),
                        id, companyId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private PageImpl<BusinessTripRow> page(long companyId, String employeeFilter, List<Object> employeeArgs,
                                           LocalDate from, LocalDate to, Long orgUnitId, RequestStatus status,
                                           Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE t.company_id = ?").append(employeeFilter);
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.addAll(employeeArgs);
        if (from != null) {
            where.append(" AND t.end_date >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND t.start_date <= ?");
            args.add(Date.valueOf(to));
        }
        if (status != null) {
            where.append(" AND t.status = ?::request_status");
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
        List<BusinessTripRow> rows = jdbc.query(SELECT + FROM + where
                        + " ORDER BY t.start_date DESC, t.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new BusinessTripRow(rs.getLong("id"), rs.getLong("employee_id"),
                        rs.getString("employee_name"), rs.getString("org_unit_name"),
                        TripType.valueOf(rs.getString("trip_type")), rs.getString("destination"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                        rs.getObject("estimated_cost", Long.class), RequestStatus.valueOf(rs.getString("status")),
                        rs.getString("report_text") != null,
                        rs.getString("claim_status") == null ? null : RequestStatus.valueOf(rs.getString("claim_status")),
                        RequestAccess.seoul(rs.getObject("created_at", OffsetDateTime.class)), null),
                args.toArray());
        Map<Long, CurrentStep> steps = approvalService.currentSteps(companyId, ApprovalWorkType.BUSINESS_TRIP,
                rows.stream().map(BusinessTripRow::id).toList());
        return new PageImpl<>(rows.stream().map(r -> r.withCurrentStep(steps.get(r.id()))).toList(), pageable, total);
    }

    private static final String SELECT = """
            SELECT t.id, t.employee_id, e.name AS employee_name, u.name AS org_unit_name, t.trip_type::text AS trip_type,
                   t.destination, t.purpose, t.start_date, t.end_date, t.estimated_cost, t.status::text AS status,
                   t.cancel_reason, t.report_text, t.reported_at, t.created_at,
                   ec.id AS claim_id, ec.status AS claim_status, ec.total AS claim_total, ec.pay_month AS claim_pay_month
            """;

    /** 경비 청구는 진행 중(승인대기·승인완료) 청구가 있으면 그것, 없으면 가장 최근 청구 하나. */
    private static final String FROM = """
             FROM business_trip t
             JOIN employee e ON e.id = t.employee_id AND e.company_id = t.company_id
             JOIN org_unit u ON u.id = e.org_unit_id AND u.company_id = e.company_id
             LEFT JOIN LATERAL (
                 SELECT c.id, c.status::text AS status, r.pay_month,
                        (SELECT COALESCE(sum(l.amount), 0) FROM expense_claim_line l
                         WHERE l.company_id = c.company_id AND l.expense_claim_id = c.id) AS total
                 FROM expense_claim c
                 LEFT JOIN paystub p ON p.id = c.paystub_id AND p.company_id = c.company_id
                 LEFT JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = c.company_id
                 WHERE c.company_id = t.company_id AND c.business_trip_id = t.id
                 ORDER BY (c.status IN ('PENDING', 'APPROVED')) DESC, c.id DESC
                 LIMIT 1
             ) ec ON TRUE
            """;
}
