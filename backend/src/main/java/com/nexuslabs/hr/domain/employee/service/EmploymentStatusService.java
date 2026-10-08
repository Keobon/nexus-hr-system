package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.account.service.AccountService;
import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.attendance.service.BusinessTripService;
import com.nexuslabs.hr.domain.attendance.service.ExpenseClaimService;
import com.nexuslabs.hr.domain.attendance.service.OvertimeService;
import com.nexuslabs.hr.domain.employee.dto.EmploymentStatusItem;
import com.nexuslabs.hr.domain.employee.dto.EmploymentStatusRequest;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.entity.EmploymentStatusHistory;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.domain.employee.repository.EmploymentStatusHistoryRepository;
import com.nexuslabs.hr.domain.leave.service.LeaveRequestService;
import com.nexuslabs.hr.domain.org.service.OrgUnitService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 재직상태 관리(F-EMP-04). 상태 변경은 이력 추가 + 현재 상태 갱신이고, 퇴직이면 같은 트랜잭션에서 연쇄 처리한다
 * (역할 분담 v2 2.3 9번 순서): ① 이력 · 상태 ② 계정 비활성 ③ 조직장 해제 ④ 승인대기 신청 자동 취소(휴가 · 연장 · 출장 · 경비)
 * ⑤ 승인자 재지정 표시. 하나라도 실패하면 전부 롤백된다.
 * 직원 행을 먼저 잠가 같은 직원의 휴가 · 출장 신청(같은 잠금)과 한 줄로 세운다 — 퇴직과 동시에 들어온 신청이 남지 않는다.
 */
@Service
public class EmploymentStatusService {

    private final EmployeeRepository employeeRepository;
    private final EmploymentStatusHistoryRepository historyRepository;
    private final AccountService accountService;
    private final OrgUnitService orgUnitService;
    private final LeaveRequestService leaveRequestService;
    private final OvertimeService overtimeService;
    private final BusinessTripService businessTripService;
    private final ExpenseClaimService expenseClaimService;
    private final ApprovalService approvalService;
    private final ScopeResolver scopeResolver;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EmploymentStatusService(EmployeeRepository employeeRepository,
                                   EmploymentStatusHistoryRepository historyRepository, AccountService accountService,
                                   OrgUnitService orgUnitService, LeaveRequestService leaveRequestService,
                                   OvertimeService overtimeService, BusinessTripService businessTripService,
                                   ExpenseClaimService expenseClaimService, ApprovalService approvalService,
                                   ScopeResolver scopeResolver, JdbcTemplate jdbc, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.historyRepository = historyRepository;
        this.accountService = accountService;
        this.orgUnitService = orgUnitService;
        this.leaveRequestService = leaveRequestService;
        this.overtimeService = overtimeService;
        this.businessTripService = businessTripService;
        this.expenseClaimService = expenseClaimService;
        this.approvalService = approvalService;
        this.scopeResolver = scopeResolver;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * 상태 전이: 재직중 ↔ 휴직, 재직중 · 휴직 → 퇴직(종단). 퇴직자 → EMPLOYEE_RESIGNED, 같은 상태 → INVALID_STATE,
     * 마지막 최고 관리자 퇴직 → LAST_SUPER_ADMIN.
     */
    @Transactional
    public EmploymentStatusItem change(LoginUser user, long employeeId, EmploymentStatusRequest request) {
        long cid = user.companyId();
        jdbc.queryForList("SELECT id FROM employee WHERE id = ? AND company_id = ? FOR UPDATE",
                Long.class, employeeId, cid);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (employee.getStatus() == EmpStatus.RESIGNED) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        if (employee.getStatus() == request.status()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 그 재직상태입니다");
        }
        checkEffectiveDate(cid, employee, request.effectiveDate());

        // ① 이력 · 현재 상태 — 뒤의 SQL(조직장 해제 등)이 바뀐 상태를 보도록 바로 반영한다
        EmploymentStatusHistory history = historyRepository.save(new EmploymentStatusHistory(employee,
                request.status(), request.effectiveDate(), request.reason().strip(), user.employeeId()));
        employee.changeStatus(request.status());
        employeeRepository.flush();

        if (request.status() == EmpStatus.RESIGNED) {
            accountService.deactivateForResignation(user, employeeId);         // ②
            orgUnitService.releaseLeadsIfLeft(cid, employeeId);                // ③
            leaveRequestService.cancelPendingByResignation(cid, employeeId);   // ④
            overtimeService.cancelPendingByResignation(cid, employeeId);
            businessTripService.cancelPendingByResignation(cid, employeeId);
            expenseClaimService.cancelPendingByResignation(cid, employeeId);
            approvalService.markReassignNeeded(cid, employeeId);               // ⑤
        }
        return item(cid, history.getId());
    }

    /** 재직상태 이력, 시간순(발효일 → 등록 순). EMPLOYEE_READ 전사 범위만 — 팀 범위면 OUT_OF_SCOPE. */
    @Transactional(readOnly = true)
    public List<EmploymentStatusItem> history(LoginUser user, long employeeId) {
        if (!scopeResolver.scopeOf(user, PermissionCode.EMPLOYEE_READ).all()) {
            throw new BusinessException(ErrorCode.OUT_OF_SCOPE);
        }
        employeeRepository.findById(employeeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return jdbc.query(SELECT + " WHERE h.employee_id = ? AND h.company_id = ? ORDER BY h.effective_date, h.id",
                this::map, employeeId, user.companyId());
    }

    // ------------------------------------------------------------------

    /** 발효일은 오늘까지, 입사일 이후, 마지막 이력의 발효일 이후(같은 날은 된다). */
    private void checkEffectiveDate(long cid, Employee employee, LocalDate date) {
        String error = null;
        if (date.isAfter(LocalDate.now(clock))) {
            error = "발효일은 오늘 이후로 정할 수 없습니다";
        } else if (date.isBefore(employee.getHireDate())) {
            error = "발효일은 입사일(" + employee.getHireDate() + ") 이후여야 합니다";
        } else {
            LocalDate last = jdbc.queryForObject("""
                            SELECT max(effective_date) FROM employment_status_history
                            WHERE employee_id = ? AND company_id = ?
                            """,
                    LocalDate.class, employee.getId(), cid);
            if (last != null && date.isBefore(last)) {
                error = "발효일은 마지막 재직상태 변경일(" + last + ") 이후여야 합니다";
            }
        }
        if (error != null) {
            throw BusinessException.invalidFields(Map.of("effectiveDate", error));
        }
    }

    private EmploymentStatusItem item(long cid, long historyId) {
        return jdbc.queryForObject(SELECT + " WHERE h.id = ? AND h.company_id = ?", this::map, historyId, cid);
    }

    private EmploymentStatusItem map(ResultSet rs, int i) throws SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new EmploymentStatusItem(rs.getLong("id"), EmpStatus.valueOf(rs.getString("status")),
                rs.getObject("effective_date", LocalDate.class), rs.getString("reason"),
                rs.getObject("created_by", Long.class), rs.getString("created_by_name"),
                createdAt.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime());
    }

    private static final String SELECT = """
            SELECT h.id, h.status::text AS status, h.effective_date, h.reason, h.created_by, c.name AS created_by_name,
                   h.created_at
            FROM employment_status_history h
            LEFT JOIN employee c ON c.id = h.created_by AND c.company_id = h.company_id
            """;
}
