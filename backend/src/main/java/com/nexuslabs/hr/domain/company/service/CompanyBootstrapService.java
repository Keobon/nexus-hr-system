package com.nexuslabs.hr.domain.company.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

/**
 * 회사 등록 때 기본값 행을 그 회사로 복사한다(기능명세서 부록 B, ERD 8장, BR-TEN-003).
 * seed_demo.sql 의 기본값 부분과 같은 구성이다 — 한쪽을 바꾸면 다른 쪽도 바꾼다.
 * 회사 등록 트랜잭션 안에서만 부른다.
 */
@Service
public class CompanyBootstrapService {

    public static final String SUPER_ADMIN_ROLE = "최고 관리자";
    public static final String EMPLOYEE_ROLE = "직원";
    public static final String REGULAR_EMPLOYMENT_TYPE = "정규직";

    private final JdbcTemplate jdbc;

    public CompanyBootstrapService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 관리자 직원을 만드는 데 필요한 ID를 돌려준다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Defaults createDefaults(long companyId, String companyName, LocalDate today) {
        long superAdminRoleId = createRoles(companyId);
        long rootOrgUnitId = jdbc.queryForObject(
                "INSERT INTO org_unit (company_id, parent_id, name) VALUES (?, NULL, ?) RETURNING id",
                Long.class, companyId, companyName);
        jdbc.update("""
                INSERT INTO employment_type (company_id, name, sort_order)
                VALUES (?, '정규직', 1), (?, '계약직', 2), (?, '인턴', 3)
                """, companyId, companyId, companyId);
        long regularTypeId = jdbc.queryForObject(
                "SELECT id FROM employment_type WHERE company_id = ? AND name = ?",
                Long.class, companyId, REGULAR_EMPLOYMENT_TYPE);
        // 직원 등록에 직급이 필수라 기본값을 넣는다(직책은 비워 둔다). 순서가 서열
        jdbc.update("""
                INSERT INTO job_grade (company_id, name, sort_order)
                VALUES (?, '사원', 1), (?, '대리', 2), (?, '과장', 3), (?, '차장', 4), (?, '부장', 5)
                """, companyId, companyId, companyId, companyId, companyId);

        createWorkSchedule(companyId, today);
        createLeaveTypes(companyId);
        createExpenseTypes(companyId);
        createApprovalLines(companyId);
        createPayItems(companyId);
        createPayVariables(companyId, today);
        createTaxBrackets(companyId);
        return new Defaults(superAdminRoleId, rootOrgUnitId, regularTypeId);
    }

    private long createRoles(long companyId) {
        long superAdmin = insertRole(companyId, SUPER_ADMIN_ROLE, "모든 권한. 시스템 역할", true);
        long hr = insertRole(companyId, "인사 담당", "인사·근태·휴가·급여·발령·평가 관리", false);
        long executive = insertRole(companyId, "경영진", "전사 조회와 대시보드", false);
        long employee = insertRole(companyId, EMPLOYEE_ROLE, "본인 셀프서비스와 팀 범위 조회", false);

        // 최고 관리자: 18개 전부 ALL
        jdbc.update("""
                INSERT INTO role_permission (company_id, role_id, permission_code, scope)
                SELECT ?, ?, p, 'ALL' FROM unnest(enum_range(NULL::permission_code)) AS p
                """, companyId, superAdmin);
        grant(companyId, hr, "ALL", "ORG_MANAGE", "EMPLOYEE_READ", "EMPLOYEE_MANAGE", "ATTENDANCE_READ",
                "ATTENDANCE_MANAGE", "LEAVE_READ", "LEAVE_MANAGE", "PAYROLL_READ", "PAYROLL_MANAGE", "ASSIGNMENT_READ",
                "ASSIGNMENT_MANAGE", "EVAL_READ", "EVAL_MANAGE", "DASHBOARD_COMPANY");
        grant(companyId, executive, "ALL", "EMPLOYEE_READ", "ATTENDANCE_READ", "LEAVE_READ", "PAYROLL_READ",
                "ASSIGNMENT_READ", "EVAL_READ", "DASHBOARD_COMPANY");
        grant(companyId, employee, "TEAM", "EMPLOYEE_READ", "ATTENDANCE_READ", "LEAVE_READ", "ASSIGNMENT_READ",
                "EVAL_READ");
        return superAdmin;
    }

    private long insertRole(long companyId, String name, String description, boolean system) {
        return jdbc.queryForObject(
                "INSERT INTO role (company_id, name, description, is_system) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, companyId, name, description, system);
    }

    private void grant(long companyId, long roleId, String scope, String... codes) {
        jdbc.update("""
                INSERT INTO role_permission (company_id, role_id, permission_code, scope)
                SELECT ?, ?, p::permission_code, ?::perm_scope FROM unnest(?::text[]) AS p
                """, companyId, roleId, scope, codes);
    }

    private void createWorkSchedule(long companyId, LocalDate today) {
        jdbc.update("""
                INSERT INTO work_schedule (company_id, effective_from, start_time, end_time, break_minutes,
                                           late_grace_minutes, night_start, night_end, overtime_approval_required, work_days)
                VALUES (?, ?, '09:00', '18:00', 60, 0, '22:00', '06:00', TRUE, 31)
                """, companyId, Date.valueOf(today));
    }

    private void createLeaveTypes(long companyId) {
        jdbc.update("""
                INSERT INTO leave_type (company_id, name, annual_days, deducts_balance, is_paid, prorate_first_year,
                                        seniority_start_years, seniority_interval_years, seniority_add_days,
                                        seniority_max_days, sort_order)
                VALUES (?, '연차',   15, TRUE,  TRUE, TRUE,  3, 2, 1, 25, 1),
                       (?, '병가',    0, FALSE, TRUE, FALSE, NULL, NULL, NULL, NULL, 2),
                       (?, '경조사',  0, FALSE, TRUE, FALSE, NULL, NULL, NULL, NULL, 3)
                """, companyId, companyId, companyId);
    }

    private void createExpenseTypes(long companyId) {
        jdbc.update("""
                INSERT INTO expense_type (company_id, name, receipt_required, sort_order)
                SELECT ?, t.n, t.r, t.o FROM (VALUES
                  ('교통비', TRUE, 1), ('숙박비', TRUE, 2), ('식비', TRUE, 3), ('일비', FALSE, 4), ('기타', TRUE, 5)
                ) AS t(n, r, o)
                """, companyId);
    }

    /** 휴가 · 연장근무 · 출장 · 출장 경비마다 기본 승인선 1개, 1단계 소속 조직장. 휴가 취소는 승인선이 없다. */
    private void createApprovalLines(long companyId) {
        jdbc.update("""
                WITH line AS (
                    INSERT INTO approval_line (company_id, name, work_type, is_default, priority)
                    SELECT ?, t.n, t.w::approval_work_type, TRUE, 100 FROM (VALUES
                      ('휴가 기본', 'LEAVE'), ('연장근무 기본', 'OVERTIME'),
                      ('출장 기본', 'BUSINESS_TRIP'), ('출장 경비 기본', 'TRIP_EXPENSE')
                    ) AS t(n, w)
                    RETURNING id
                )
                INSERT INTO approval_line_step (company_id, approval_line_id, step_order, approver_type)
                SELECT ?, id, 1, 'ORG_LEAD' FROM line
                """, companyId, companyId);
    }

    private void createPayItems(long companyId) {
        // 4대보험(공제 · 과세액 대비 요율 · 전원)
        jdbc.update("""
                INSERT INTO pay_item (company_id, name, item_kind, is_taxable, calc_method, apply_to,
                                      employee_rate, company_rate, sort_order)
                SELECT ?, i.n, 'DEDUCTION', TRUE, 'TAXABLE_RATE', 'ALL', i.er, i.cr, 100 + i.o FROM (VALUES
                  ('국민연금', 4.5, 4.5, 1), ('건강보험', 3.545, 3.545, 2),
                  ('장기요양보험', 0.4591, 0.4591, 3), ('고용보험', 0.9, 1.15, 4)
                ) AS i(n, er, cr, o)
                """, companyId);
        // 근태 연동 수당 · 결근 공제
        jdbc.update("""
                INSERT INTO pay_item (company_id, name, item_kind, is_taxable, calc_method, attendance_basis,
                                      multiplier, sort_order)
                SELECT ?, i.n, i.k::pay_item_kind, TRUE, 'ATTENDANCE', i.b::attendance_basis, i.m, 50 + i.o FROM (VALUES
                  ('연장근로수당', 'EARNING', 'OVERTIME', 1.5, 1), ('야간근로수당', 'EARNING', 'NIGHT', 0.5, 2),
                  ('휴일근로수당', 'EARNING', 'HOLIDAY', 1.5, 3), ('휴일연장근로수당', 'EARNING', 'HOLIDAY_OVERTIME', 2.0, 4),
                  ('결근 공제', 'DEDUCTION', 'ABSENCE', 1.0, 5)
                ) AS i(n, k, b, m, o)
                """, companyId);
        jdbc.update("""
                INSERT INTO pay_item (company_id, name, item_kind, is_taxable, calc_method, sort_order)
                VALUES (?, '출장비 정산', 'EARNING', FALSE, 'TRIP_EXPENSE', 60)
                """, companyId);
    }

    private void createPayVariables(long companyId, LocalDate today) {
        jdbc.update("""
                INSERT INTO pay_variable (company_id, var_code, value, effective_from)
                SELECT ?, v.code::pay_var_code, v.val, ? FROM (VALUES
                  ('DEPENDENT_DEDUCTION', 125000), ('MULTI_CHILD_DEDUCTION', 0), ('LOCAL_TAX_RATE', 10),
                  ('ANNUAL_SPLIT_MONTHS', 12), ('MONTHLY_STANDARD_HOURS', 209)
                ) AS v(code, val)
                """, companyId, Date.valueOf(today));
    }

    private void createTaxBrackets(long companyId) {
        jdbc.update("""
                INSERT INTO tax_bracket (company_id, lower_bound, upper_bound, rate, progressive_deduction)
                SELECT ?, b.lo, b.hi, b.r, b.d FROM (VALUES
                  (0::bigint, 1200000::bigint, 6, 0), (1200000, 4200000, 15, 108000), (4200000, 7400000, 24, 486000),
                  (7400000, 12500000, 35, 1300000), (12500000, NULL, 38, 1675000)
                ) AS b(lo, hi, r, d)
                """, companyId);
    }

    public record Defaults(long superAdminRoleId, long rootOrgUnitId, long regularEmploymentTypeId) {
    }
}
