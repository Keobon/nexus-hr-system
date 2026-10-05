package com.nexuslabs.hr.domain.account.service;

import com.nexuslabs.hr.domain.account.dto.MeResponse;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import com.nexuslabs.hr.global.permission.PermissionScope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * GET /api/me. 로그인 직후와 화면 이동 때 불리므로 쿼리 몇 개로 끝낸다.
 * 할 일 건수의 기준은 각 영역(승인 B-07 · 평가 B-16 · 근태 정정 B-11)의 정의를 따른다 — 정의가 바뀌면 여기도 맞춘다.
 */
@Service
public class MeService {

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;
    private final ScopeResolver scopeResolver;

    public MeService(JdbcTemplate jdbc, PermissionReader permissionReader, ScopeResolver scopeResolver) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
        this.scopeResolver = scopeResolver;
    }

    @Transactional(readOnly = true)
    public MeResponse me(LoginUser user) {
        long cid = user.companyId();
        MeResponse.EmployeeSummary employee = jdbc.queryForObject("""
                        SELECT e.id, e.employee_no, e.name, e.org_unit_id, o.name AS org_unit_name,
                               g.name AS job_grade_name, t.name AS job_title_name, e.profile_file_id, e.payroll_eligible
                        FROM employee e
                        JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                        LEFT JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                        LEFT JOIN job_title t ON t.id = e.job_title_id AND t.company_id = e.company_id
                        WHERE e.id = ? AND e.company_id = ?
                        """,
                (rs, i) -> new MeResponse.EmployeeSummary(rs.getLong("id"), rs.getString("employee_no"),
                        rs.getString("name"), rs.getLong("org_unit_id"), rs.getString("org_unit_name"),
                        rs.getString("job_grade_name"), rs.getString("job_title_name"),
                        rs.getObject("profile_file_id", Long.class), rs.getBoolean("payroll_eligible")),
                user.employeeId(), cid);
        MeResponse.CompanySummary company = jdbc.queryForObject(
                "SELECT id, name, logo_file_id, setup_completed FROM company WHERE id = ?",
                (rs, i) -> new MeResponse.CompanySummary(rs.getLong("id"), rs.getString("name"),
                        rs.getObject("logo_file_id", Long.class), rs.getBoolean("setup_completed")),
                cid);
        MeResponse.RoleSummary role = jdbc.queryForObject(
                "SELECT id, name FROM role WHERE id = ? AND company_id = ?",
                (rs, i) -> new MeResponse.RoleSummary(rs.getLong("id"), rs.getString("name")),
                user.roleId(), cid);

        Map<PermissionCode, PermissionScope> granted = permissionReader.permissionsOf(user);
        List<MeResponse.PermissionGrant> permissions = granted.entrySet().stream()
                .map(e -> new MeResponse.PermissionGrant(e.getKey(), e.getValue()))
                .toList();
        List<Long> leadOrgUnitIds = scopeResolver.leadOrgUnitIds(user);

        return new MeResponse(employee, company, role, permissions, !leadOrgUnitIds.isEmpty(), leadOrgUnitIds,
                user.mustChangePassword(), todos(user, granted));
    }

    private MeResponse.Todos todos(LoginUser user, Map<PermissionCode, PermissionScope> granted) {
        long cid = user.companyId();
        long approvalsPending = count("""
                SELECT count(*) FROM approval_step
                WHERE company_id = ? AND approver_id = ? AND status = 'PENDING'
                """, cid, user.employeeId());
        // 진행 중인 기간에서 아직 제출하지 않은 평가 + 재오픈된 평가
        long evaluationsToSubmit = count("""
                SELECT count(*) FROM evaluation e
                JOIN eval_cycle c ON c.id = e.eval_cycle_id AND c.company_id = e.company_id
                WHERE e.company_id = ? AND e.evaluator_id = ?
                  AND ((c.status = 'IN_PROGRESS' AND e.status IN ('NOT_STARTED', 'IN_PROGRESS')) OR e.status = 'REOPENED')
                """, cid, user.employeeId());
        Long reassignNeeded = granted.containsKey(PermissionCode.APPROVAL_MANAGE)
                ? count("SELECT count(*) FROM approval_step WHERE company_id = ? AND status = 'PENDING' AND needs_reassign", cid)
                : null;
        // 정정 대상(F-ATT-04): 퇴근미기록 + 출근 기록이 있는 날에 휴가·출장이 승인된 충돌
        Long attendanceCorrections = granted.containsKey(PermissionCode.ATTENDANCE_MANAGE)
                ? count("""
                SELECT count(*) FROM attendance a
                WHERE a.company_id = ?
                  AND (a.status = 'MISSING_CHECKOUT'
                       OR (a.check_in_at IS NOT NULL AND (
                            EXISTS (SELECT 1 FROM leave_request l
                                    WHERE l.company_id = a.company_id AND l.employee_id = a.employee_id
                                      AND l.status IN ('APPROVED', 'CANCEL_REQUESTED')
                                      AND a.work_date BETWEEN l.start_date AND l.end_date)
                         OR EXISTS (SELECT 1 FROM business_trip b
                                    WHERE b.company_id = a.company_id AND b.employee_id = a.employee_id
                                      AND b.status = 'APPROVED'
                                      AND a.work_date BETWEEN b.start_date AND b.end_date))))
                """, cid)
                : null;
        return new MeResponse.Todos(approvalsPending, evaluationsToSubmit, reassignNeeded, attendanceCorrections);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
