package com.nexuslabs.hr.domain.leave.service;

import com.nexuslabs.hr.domain.leave.dto.EmployeeLeaveBalances;
import com.nexuslabs.hr.domain.leave.dto.LeaveBalance;
import com.nexuslabs.hr.domain.leave.dto.MyLeaveBalances;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 잔여 조회(F-LEAVE-05). 본인 / LEAVE_READ(팀·전사). 퇴직자는 목록에서 뺀다. */
@Service
public class LeaveBalanceService {

    private final JdbcTemplate jdbc;
    private final LeaveCalculator calculator;
    private final ScopeResolver scopeResolver;

    public LeaveBalanceService(JdbcTemplate jdbc, LeaveCalculator calculator, ScopeResolver scopeResolver) {
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.scopeResolver = scopeResolver;
    }

    /** leaveYear 가 null 이면 오늘이 속한 휴가 연도. */
    @Transactional(readOnly = true)
    public MyLeaveBalances mine(LoginUser user, Integer leaveYear) {
        LeaveYear year = leaveYear == null
                ? calculator.currentLeaveYear(user.companyId())
                : calculator.leaveYear(user.companyId(), leaveYear);
        List<LeaveBalance> balances = calculator.balances(user.companyId(), year.year(), List.of(user.employeeId()))
                .getOrDefault(user.employeeId(), List.of());
        return new MyLeaveBalances(year.year(), year.start(), year.end(), balances);
    }

    /** orgUnitId 를 주면 그 조직과 하위 조직 소속만. 팀 범위면 내 팀 직원만. 사원번호 순. */
    @Transactional(readOnly = true)
    public PageImpl<EmployeeLeaveBalances> list(LoginUser user, Integer leaveYear, Long orgUnitId, Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.LEAVE_READ);
        int year = leaveYear != null ? leaveYear : calculator.currentLeaveYear(user.companyId()).year();
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        StringBuilder where = new StringBuilder(" WHERE e.company_id = ? AND e.status <> 'RESIGNED'");
        List<Object> args = new ArrayList<>(List.of(user.companyId()));
        if (!scope.all()) {
            where.append(" AND e.id = ANY(?)");
            args.add(scope.employeeIds().toArray(Long[]::new));
        }
        if (orgUnitId != null) {
            where.append("""
                     AND e.org_unit_id IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT o.id FROM org_unit o JOIN sub s ON o.parent_id = s.id WHERE o.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(orgUnitId, user.companyId(), user.companyId()));
        }
        String from = " FROM employee e JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id";

        long total = jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
        args.addAll(List.of(pageable.getPageSize(), pageable.getOffset()));
        List<EmployeeLeaveBalances> rows = jdbc.query(
                "SELECT e.id, e.employee_no, e.name, o.name AS org_unit_name" + from + where
                        + " ORDER BY e.employee_no, e.id LIMIT ? OFFSET ?",
                (rs, i) -> new EmployeeLeaveBalances(rs.getLong("id"), rs.getString("employee_no"),
                        rs.getString("name"), rs.getString("org_unit_name"), List.of()),
                args.toArray());

        Map<Long, List<LeaveBalance>> balances = calculator.balances(user.companyId(), year,
                rows.stream().map(EmployeeLeaveBalances::employeeId).toList());
        List<EmployeeLeaveBalances> content = rows.stream()
                .map(r -> new EmployeeLeaveBalances(r.employeeId(), r.employeeNo(), r.name(), r.orgUnitName(),
                        balances.getOrDefault(r.employeeId(), List.of())))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }
}
