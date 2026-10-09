package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceSummary;
import com.nexuslabs.hr.domain.attendance.service.AttendanceCalculator;
import com.nexuslabs.hr.domain.attendance.service.TodayStatus;
import com.nexuslabs.hr.domain.attendance.service.TodayStatusReader;
import com.nexuslabs.hr.domain.employee.dto.EmployeeDetail;
import com.nexuslabs.hr.domain.employee.dto.EmployeeRow;
import com.nexuslabs.hr.domain.employee.dto.FamilyList;
import com.nexuslabs.hr.domain.employee.dto.MyProfileResponse;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Gender;
import com.nexuslabs.hr.domain.leave.service.LeaveBalanceService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 직원 목록(F-EMP-02) · 상세(F-EMP-06)와 개인 페이지(F-EMP-05) 조회. 여러 테이블을 묶는 조회라 JDBC 로 하고,
 * JDBC 는 @TenantId 자동 필터가 없으므로 모든 쿼리에 company_id 조건을 직접 넣는다.
 */
@Service
public class EmployeeQueryService {

    private static final List<EmpStatus> DEFAULT_STATUSES = List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE);

    /** 정렬에 쓸 수 있는 항목. 여기 없는 이름은 무시한다. */
    private static final Map<String, String> SORTABLE = Map.of(
            "employeeNo", "e.employee_no",
            "name", "e.name",
            "hireDate", "e.hire_date",
            "orgUnitName", "o.name",
            "jobGradeName", "g.sort_order",
            "status", "e.status");

    private static final String FROM = """
             FROM employee e
                  JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                  JOIN employment_type et ON et.id = e.employment_type_id AND et.company_id = e.company_id
                  LEFT JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                  LEFT JOIN job_title t ON t.id = e.job_title_id AND t.company_id = e.company_id
            """;

    /** 오늘 유효한 기본 급여의 연환산 — 연봉제는 연봉, 월급제는 월 기본급 × 12(F-EMP-02). */
    private static final String ANNUAL_SALARY = """
            (SELECT CASE WHEN s.salary_type = 'ANNUAL' THEN s.annual_salary ELSE s.monthly_base * 12 END
             FROM employee_salary s
             WHERE s.company_id = e.company_id AND s.employee_id = e.id AND s.effective_from <= ?
             ORDER BY s.effective_from DESC, s.created_at DESC, s.id DESC LIMIT 1)""";

    private final JdbcTemplate jdbc;
    private final ScopeResolver scopeResolver;
    private final PermissionReader permissionReader;
    private final TodayStatusReader todayStatusReader;
    private final LeaveBalanceService leaveBalanceService;
    private final EmployeeFieldValueService fieldValueService;
    private final EmployeeFamilyService familyService;
    private final AttendanceCalculator attendanceCalculator;
    private final Clock clock;

    public EmployeeQueryService(JdbcTemplate jdbc, ScopeResolver scopeResolver, PermissionReader permissionReader,
                                TodayStatusReader todayStatusReader, LeaveBalanceService leaveBalanceService,
                                EmployeeFieldValueService fieldValueService, EmployeeFamilyService familyService,
                                AttendanceCalculator attendanceCalculator, Clock clock) {
        this.jdbc = jdbc;
        this.scopeResolver = scopeResolver;
        this.permissionReader = permissionReader;
        this.todayStatusReader = todayStatusReader;
        this.leaveBalanceService = leaveBalanceService;
        this.fieldValueService = fieldValueService;
        this.familyService = familyService;
        this.attendanceCalculator = attendanceCalculator;
        this.clock = clock;
    }

    /**
     * 직원 목록. 서버가 권한 범위를 다시 계산해 범위 안의 직원만 돌려준다(BR-AUTH-001) —
     * 팀 범위인 사람이 범위 밖 조직을 필터로 넣어도 범위 안만 나온다. statuses 를 비우면 퇴직자를 뺀다.
     */
    @Transactional(readOnly = true)
    public PageImpl<EmployeeRow> list(LoginUser user, Long orgUnitId, Long jobGradeId, Long jobTitleId,
                                      Long employmentTypeId, List<String> statuses, String keyword,
                                      Pageable pageable) {
        String[] statusNames = statusNames(statuses);
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.EMPLOYEE_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        boolean showSalary = permissionReader.permissionsOf(user).containsKey(PermissionCode.PAYROLL_READ);

        StringBuilder where = new StringBuilder(" WHERE e.company_id = ? AND e.status::text = ANY(?)");
        List<Object> args = new ArrayList<>();
        args.add(user.companyId());
        args.add(statusNames);
        if (!scope.all()) {
            where.append(" AND e.id = ANY(?)");
            args.add(scope.employeeIds().toArray(Long[]::new));
        }
        if (orgUnitId != null) {
            where.append("""
                     AND e.org_unit_id IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT c.id FROM org_unit c JOIN sub s ON c.parent_id = s.id WHERE c.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(orgUnitId, user.companyId(), user.companyId()));
        }
        if (jobGradeId != null) {
            where.append(" AND e.job_grade_id = ?");
            args.add(jobGradeId);
        }
        if (jobTitleId != null) {
            where.append(" AND e.job_title_id = ?");
            args.add(jobTitleId);
        }
        if (employmentTypeId != null) {
            where.append(" AND e.employment_type_id = ?");
            args.add(employmentTypeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.name ILIKE ? OR e.employee_no ILIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.addAll(List.of(like, like));
        }
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>();
        if (showSalary) {
            pageArgs.add(Date.valueOf(LocalDate.now(clock)));       // SELECT 절의 연봉 계산
        }
        pageArgs.addAll(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<EmployeeRow> rows = jdbc.query("""
                        SELECT e.id, e.employee_no, e.name, e.org_unit_id, o.name AS org_unit_name, g.name AS job_grade_name,
                               t.name AS job_title_name, et.name AS employment_type_name, e.status::text AS status,
                               e.hire_date,
                        """ + (showSalary ? ANNUAL_SALARY : "NULL::bigint") + " AS annual_salary"
                        + FROM + where + orderBy(pageable.getSort()) + " LIMIT ? OFFSET ?",
                (rs, i) -> new EmployeeRow(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                        rs.getLong("org_unit_id"), rs.getString("org_unit_name"), rs.getString("job_grade_name"),
                        rs.getString("job_title_name"), rs.getString("employment_type_name"),
                        EmpStatus.valueOf(rs.getString("status")), null, rs.getObject("hire_date", LocalDate.class),
                        showSalary ? Optional.ofNullable(rs.getObject("annual_salary", Long.class)) : null),
                pageArgs.toArray());

        Map<Long, TodayStatus> today = todayStatusReader.today(user.companyId(), rows.stream().map(EmployeeRow::id).toList());
        return new PageImpl<>(rows.stream().map(r -> withTodayStatus(r, today.get(r.id()))).toList(), pageable, total);
    }

    /** 개인 페이지. 토큰의 직원 ID 로만 조회한다 — 주소에 다른 직원 ID 를 넣을 자리가 없다(BR-AUTH-001). */
    @Transactional(readOnly = true)
    public MyProfileResponse myProfile(LoginUser user) {
        List<MyProfileResponse.FieldValue> fieldValues = fieldValueService.values(user.companyId(), user.employeeId());
        List<MyProfileResponse.LeaveBalance> leaveBalances = leaveBalanceService.mine(user, null).balances().stream()
                .map(b -> new MyProfileResponse.LeaveBalance(b.leaveTypeName(), b.remaining()))
                .toList();

        return jdbc.query("""
                        SELECT e.id, e.employee_no, e.name, e.name_en, e.email, e.phone, e.address, e.birth_date,
                               e.gender::text AS gender, e.emergency_name, e.emergency_relation, e.emergency_phone,
                               e.hire_date, e.org_unit_id, o.name AS org_unit_name, g.name AS job_grade_name,
                               t.name AS job_title_name, et.name AS employment_type_name, e.status::text AS status,
                               e.payroll_eligible, e.contract_end_date, e.probation_end_date, e.profile_file_id
                        """ + FROM + " WHERE e.company_id = ? AND e.id = ?",
                (rs, i) -> new MyProfileResponse(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                        rs.getString("name_en"), rs.getString("email"), rs.getString("phone"), rs.getString("address"),
                        rs.getObject("birth_date", LocalDate.class),
                        rs.getString("gender") == null ? null : Gender.valueOf(rs.getString("gender")),
                        rs.getString("emergency_name"), rs.getString("emergency_relation"),
                        rs.getString("emergency_phone"), rs.getObject("hire_date", LocalDate.class),
                        rs.getLong("org_unit_id"), rs.getString("org_unit_name"), rs.getString("job_grade_name"),
                        rs.getString("job_title_name"), rs.getString("employment_type_name"),
                        EmpStatus.valueOf(rs.getString("status")), rs.getBoolean("payroll_eligible"),
                        rs.getObject("contract_end_date", LocalDate.class),
                        rs.getObject("probation_end_date", LocalDate.class),
                        rs.getObject("profile_file_id", Long.class), fieldValues, leaveBalances),
                user.companyId(), user.employeeId()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * 직원 상세. 다른 회사 직원이면 404, 팀 범위 밖이면 OUT_OF_SCOPE. 팀 범위면 제한 필드만(BR-EMP-003 — 주소 · 부양가족 · 자녀 · 추가 항목 숨김),
     * 인사 메모는 EMPLOYEE_MANAGE 만. 두 범위 모두 오늘 상태와 이번 달 근태 요약을 붙인다.
     */
    @Transactional(readOnly = true)
    public EmployeeDetail detail(LoginUser user, long employeeId) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.EMPLOYEE_READ);
        long companyId = user.companyId();
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee WHERE id = ? AND company_id = ?)",
                Boolean.class, employeeId, companyId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        scope.assertContains(employeeId);
        boolean manage = permissionReader.permissionsOf(user).containsKey(PermissionCode.EMPLOYEE_MANAGE);

        YearMonth month = YearMonth.now(clock);
        AttendanceSummary s = attendanceCalculator.month(companyId, employeeId, month).summary();
        EmployeeDetail.MonthSummary monthSummary = new EmployeeDetail.MonthSummary(month.toString(), s.lateCount(),
                s.earlyLeaveCount(), s.absentDays(), s.remoteDays(), s.fieldDays());
        TodayStatus todayStatus = todayStatusReader.today(companyId, List.of(employeeId)).get(employeeId);
        List<MyProfileResponse.FieldValue> fieldValues =
                scope.all() ? fieldValueService.values(companyId, employeeId) : null;
        FamilyList family = scope.all() ? familyService.list(companyId, employeeId) : null;

        return jdbc.query("""
                        SELECT e.id, e.employee_no, e.name, e.name_en, e.email, e.phone, e.address, e.birth_date,
                               e.gender::text AS gender, e.emergency_name, e.emergency_relation, e.emergency_phone,
                               e.hire_date, e.org_unit_id, o.name AS org_unit_name, e.job_grade_id,
                               g.name AS job_grade_name, e.job_title_id, t.name AS job_title_name,
                               e.employment_type_id, et.name AS employment_type_name, e.status::text AS status,
                               e.payroll_eligible, e.contract_end_date, e.probation_end_date, e.profile_file_id,
                               e.hr_memo
                        """ + FROM + " WHERE e.company_id = ? AND e.id = ?",
                (rs, i) -> new EmployeeDetail(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                        rs.getString("org_unit_name"), rs.getString("job_grade_name"), rs.getString("job_title_name"),
                        rs.getString("employment_type_name"), EmpStatus.valueOf(rs.getString("status")),
                        rs.getString("email"), rs.getString("phone"), rs.getObject("profile_file_id", Long.class),
                        todayStatus, monthSummary,
                        !scope.all() ? null : new EmployeeDetail.Full(rs.getString("name_en"), rs.getString("address"),
                                rs.getObject("birth_date", LocalDate.class),
                                rs.getString("gender") == null ? null : Gender.valueOf(rs.getString("gender")),
                                rs.getString("emergency_name"), rs.getString("emergency_relation"),
                                rs.getString("emergency_phone"), rs.getObject("hire_date", LocalDate.class),
                                rs.getLong("org_unit_id"), rs.getObject("job_grade_id", Long.class),
                                rs.getObject("job_title_id", Long.class), rs.getLong("employment_type_id"),
                                rs.getBoolean("payroll_eligible"), rs.getObject("contract_end_date", LocalDate.class),
                                rs.getObject("probation_end_date", LocalDate.class), fieldValues,
                                family.dependentsCount(), family.childrenCount()),
                        manage ? Optional.ofNullable(rs.getString("hr_memo")) : null),
                companyId, employeeId).getFirst();
    }

    /** 재직상태 필터. 비우면 재직 · 휴직. 없는 코드값은 INVALID_ENUM_VALUE(API 설계서 1.1). */
    private static String[] statusNames(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return DEFAULT_STATUSES.stream().map(Enum::name).toArray(String[]::new);
        }
        List<String> allowed = Arrays.stream(EmpStatus.values()).map(Enum::name).toList();
        if (!allowed.containsAll(statuses)) {
            throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE, Map.of("allowed", allowed));
        }
        return statuses.toArray(String[]::new);
    }

    private static EmployeeRow withTodayStatus(EmployeeRow r, TodayStatus todayStatus) {
        return new EmployeeRow(r.id(), r.employeeNo(), r.name(), r.orgUnitId(), r.orgUnitName(), r.jobGradeName(),
                r.jobTitleName(), r.employmentTypeName(), r.status(), todayStatus, r.hireDate(), r.annualSalary());
    }

    private static String orderBy(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = SORTABLE.get(order.getProperty());
            if (column != null) {
                parts.add(column + (order.isAscending() ? " ASC" : " DESC") + " NULLS LAST");
            }
        }
        parts.add("e.employee_no ASC");
        parts.add("e.id ASC");
        return " ORDER BY " + String.join(", ", parts);
    }
}
