package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.dto.EmployeeMonthlySummary;
import com.nexuslabs.hr.domain.attendance.dto.EmployeeTodayAttendance;
import com.nexuslabs.hr.domain.attendance.dto.MonthlyAttendance;
import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 근태 조회(F-ATT-03) — 본인 / ATTENDANCE_READ(팀 · 전사). 계산은 AttendanceCalculator 가 하고 여기서는 누구 것을 볼 수 있는지만 정한다.
 * 여러 테이블을 묶는 조회라 JDBC 로 하고, 모든 쿼리에 company_id 조건을 직접 넣는다.
 */
@Service
public class AttendanceQueryService {

    /** 정렬에 쓸 수 있는 항목. 여기 없는 이름은 무시한다. */
    private static final Map<String, String> SORTABLE = Map.of("employeeNo", "e.employee_no", "name", "e.name");

    private final JdbcTemplate jdbc;
    private final AttendanceCalculator calculator;
    private final ScopeResolver scopeResolver;
    private final TodayStatusReader todayStatusReader;
    private final Clock clock;

    public AttendanceQueryService(JdbcTemplate jdbc, AttendanceCalculator calculator, ScopeResolver scopeResolver,
                                  TodayStatusReader todayStatusReader, Clock clock) {
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.scopeResolver = scopeResolver;
        this.todayStatusReader = todayStatusReader;
        this.clock = clock;
    }

    /** 내 근태. 토큰의 직원으로만 조회한다(BR-AUTH-001). month 가 null 이면 이번 달. */
    @Transactional(readOnly = true)
    public MonthlyAttendance mine(LoginUser user, YearMonth month) {
        return calculator.month(user.companyId(), user.employeeId(), orCurrent(month));
    }

    /** 한 직원의 근태. 다른 회사 직원이면 404, 팀 범위 밖이면 OUT_OF_SCOPE. */
    @Transactional(readOnly = true)
    public MonthlyAttendance ofEmployee(LoginUser user, long employeeId, YearMonth month) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.ATTENDANCE_READ);
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM employee WHERE id = ? AND company_id = ?)",
                Boolean.class, employeeId, user.companyId());
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        scope.assertContains(employeeId);
        return calculator.month(user.companyId(), employeeId, orCurrent(month));
    }

    /**
     * 직원별 월 합계. 그 달에 하루라도 재직한 직원이 대상이다(그 달에 퇴직한 사람 포함).
     * 팀 범위면 내 팀 직원만 — 범위 밖 조직·직원을 필터로 넣어도 범위 안만 나온다. orgUnitId 는 하위 조직까지 포함한다.
     */
    @Transactional(readOnly = true)
    public PageImpl<EmployeeMonthlySummary> list(LoginUser user, YearMonth month, Long orgUnitId, Long employeeId,
                                                 Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.ATTENDANCE_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        YearMonth target = orCurrent(month);
        // 퇴직 발효일부터 퇴직이다(F-EMP-04) — 발효일이 그 달 1일 이전이면 그 달에는 하루도 재직하지 않았다
        StringBuilder where = new StringBuilder("""
                 WHERE e.company_id = ? AND e.hire_date <= ?
                   AND NOT EXISTS (SELECT 1 FROM employment_status_history h
                                   WHERE h.company_id = e.company_id AND h.employee_id = e.id
                                     AND h.status = 'RESIGNED' AND h.effective_date <= ?)
                """);
        List<Object> args = new ArrayList<>(List.of(user.companyId(), Date.valueOf(target.atEndOfMonth()),
                Date.valueOf(target.atDay(1))));
        if (!scope.all()) {
            where.append(" AND e.id = ANY(?)");
            args.add(scope.employeeIds().toArray(Long[]::new));
        }
        if (employeeId != null) {
            where.append(" AND e.id = ?");
            args.add(employeeId);
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
        List<EmployeeMonthlySummary> rows = jdbc.query(
                "SELECT e.id, e.employee_no, e.name, o.name AS org_unit_name" + from + where
                        + orderBy(pageable.getSort()) + " LIMIT ? OFFSET ?",
                (rs, i) -> new EmployeeMonthlySummary(rs.getLong("id"), rs.getString("employee_no"),
                        rs.getString("name"), rs.getString("org_unit_name"), null),
                args.toArray());
        Map<Long, MonthlyAttendance> months = calculator.months(user.companyId(),
                rows.stream().map(EmployeeMonthlySummary::employeeId).toList(), target);
        List<EmployeeMonthlySummary> content = rows.stream()
                .map(r -> new EmployeeMonthlySummary(r.employeeId(), r.employeeNo(), r.name(), r.orgUnitName(),
                        months.get(r.employeeId()).summary()))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 직원들의 오늘 근태(역할 분담 2.3 K 85) — 화면이 직원마다 조회하지 않도록 한 번에 준다. 퇴직자는 빠지고 휴직자는 휴직으로 나온다.
     * 범위와 orgUnitId 는 {@link #list}와 같다. 오늘 상태는 {@link TodayStatusReader}와 같은 규칙으로 읽는다 —
     * 오늘 행이 없고 자정을 넘겨 어제 근무가 이어지는 중이면 어제 행을 쓴다(F-ATT-02).
     */
    @Transactional(readOnly = true)
    public PageImpl<EmployeeTodayAttendance> today(LoginUser user, Long orgUnitId, Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.ATTENDANCE_READ);
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

        LocalDate today = LocalDate.now(clock);
        List<Object> rowArgs = new ArrayList<>(List.of(Date.valueOf(today), Date.valueOf(today.minusDays(1)),
                todayStatusReader.yesterdayStillOpen(user.companyId())));
        rowArgs.addAll(args);
        rowArgs.addAll(List.of(pageable.getPageSize(), pageable.getOffset()));
        List<EmployeeTodayAttendance> rows = jdbc.query("""
                        SELECT e.id, e.name, o.name AS org_unit_name, e.status::text AS emp_status,
                               t.status::text AS attendance_status, t.work_type::text AS work_type, t.place_memo,
                               t.check_in_at, t.check_out_at
                        """ + from + """
                         LEFT JOIN attendance a ON a.employee_id = e.id AND a.company_id = e.company_id
                                               AND a.work_date = ?
                         LEFT JOIN attendance y ON y.employee_id = e.id AND y.company_id = e.company_id
                                               AND y.work_date = ? AND y.status = 'CHECKED_IN' AND ?
                         LEFT JOIN attendance t ON t.id = COALESCE(a.id, y.id) AND t.company_id = e.company_id
                        """ + where + orderBy(pageable.getSort()) + " LIMIT ? OFFSET ?",
                (rs, i) -> {
                    String attendance = rs.getString("attendance_status");
                    WorkType workType = rs.getString("work_type") == null ? null
                            : WorkType.valueOf(rs.getString("work_type"));
                    return new EmployeeTodayAttendance(rs.getLong("id"), rs.getString("name"),
                            rs.getString("org_unit_name"),
                            TodayStatusReader.resolve(EmpStatus.valueOf(rs.getString("emp_status")),
                                    attendance == null ? null : AttendanceStatus.valueOf(attendance), workType),
                            workType, rs.getString("place_memo"),
                            RequestAccess.seoul(rs.getObject("check_in_at", OffsetDateTime.class)),
                            RequestAccess.seoul(rs.getObject("check_out_at", OffsetDateTime.class)));
                },
                rowArgs.toArray());
        return new PageImpl<>(rows, pageable, total);
    }

    private YearMonth orCurrent(YearMonth month) {
        return month != null ? month : YearMonth.now(clock);
    }

    private static String orderBy(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = SORTABLE.get(order.getProperty());
            if (column != null) {
                parts.add(column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        parts.add("e.employee_no ASC");
        parts.add("e.id ASC");
        return " ORDER BY " + String.join(", ", parts);
    }
}
