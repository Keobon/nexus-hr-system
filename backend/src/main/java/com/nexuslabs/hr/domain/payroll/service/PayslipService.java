package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.dto.LaborCost;
import com.nexuslabs.hr.domain.payroll.dto.LatestPayslip;
import com.nexuslabs.hr.domain.payroll.dto.MyPayslipRow;
import com.nexuslabs.hr.domain.payroll.dto.PayslipLine;
import com.nexuslabs.hr.domain.payroll.dto.PayslipRow;
import com.nexuslabs.hr.domain.payroll.dto.PayslipView;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;
import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 급여명세서 조회(F-PAY-06) · 조직별 인건비(F-PAY-07). 명세서는 확정 즉시 본인에게 공개되고, 정산 당시 저장된 값만 보여 준다.
 * 본인 명세서에는 회사부담이 없다. 다른 직원 명세서는 PAYROLL_READ(전사 — 급여에는 팀 범위가 없다).
 */
@Service
public class PayslipService {

    private final JdbcTemplate jdbc;

    public PayslipService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 내 명세서 목록 — 최근 귀속 월부터. */
    @Transactional(readOnly = true)
    public List<MyPayslipRow> mine(LoginUser user) {
        return jdbc.query("""
                        SELECT p.id, r.pay_month, r.pay_date, r.status::text AS status, p.net_pay
                        FROM paystub p JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = p.company_id
                        WHERE p.company_id = ? AND p.employee_id = ? ORDER BY r.pay_month DESC
                        """,
                (rs, i) -> new MyPayslipRow(rs.getLong("id"), rs.getString("pay_month"),
                        rs.getObject("pay_date", LocalDate.class), PayrollStatus.valueOf(rs.getString("status")),
                        rs.getLong("net_pay")),
                user.companyId(), user.employeeId());
    }

    /** 내 명세서 — 남의 명세서는 404. 회사부담 필드가 없다. */
    @Transactional(readOnly = true)
    public PayslipView myPayslip(LoginUser user, long id) {
        PayslipView view = view(user.companyId(), id);
        if (view.employeeId() != user.employeeId()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return view.forEmployee();
    }

    /** 명세서 상세(PAYROLL_READ) — 회사부담 포함. */
    @Transactional(readOnly = true)
    public PayslipView payslip(long companyId, long id) {
        return view(companyId, id);
    }

    /** 명세서 목록(PAYROLL_READ) — payMonth · orgUnitId(정산 당시 소속, 하위 조직 포함). 최근 귀속 월 · 사원번호 순. */
    @Transactional(readOnly = true)
    public PageImpl<PayslipRow> list(long companyId, YearMonth payMonth, Long orgUnitId, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE p.company_id = ?");
        List<Object> args = new ArrayList<>(List.of(companyId));
        if (payMonth != null) {
            where.append(" AND r.pay_month = ?");
            args.add(payMonth.toString());
        }
        if (orgUnitId != null) {
            where.append("""
                     AND p.org_unit_id_snap IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT c.id FROM org_unit c JOIN sub s ON c.parent_id = s.id WHERE c.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(orgUnitId, companyId, companyId));
        }
        String from = " FROM paystub p JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = p.company_id";
        long total = jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
        args.addAll(List.of(pageable.getPageSize(), pageable.getOffset()));
        List<PayslipRow> rows = jdbc.query("""
                        SELECT p.id, r.pay_month, r.pay_date, p.employee_id, p.employee_no_snap, p.employee_name_snap,
                               p.org_name_snap, p.gross_pay, p.total_deduction, p.net_pay, p.company_burden_total
                        """ + from + where + " ORDER BY r.pay_month DESC, p.employee_no_snap, p.id LIMIT ? OFFSET ?",
                (rs, i) -> new PayslipRow(rs.getLong("id"), rs.getString("pay_month"),
                        rs.getObject("pay_date", LocalDate.class), rs.getLong("employee_id"),
                        rs.getString("employee_no_snap"), rs.getString("employee_name_snap"),
                        rs.getString("org_name_snap"), rs.getLong("gross_pay"), rs.getLong("total_deduction"),
                        rs.getLong("net_pay"), rs.getLong("company_burden_total")),
                args.toArray());
        return new PageImpl<>(rows, pageable, total);
    }

    /** 홈 me.latestPayslip(역할 분담 v2 2.1, B-17 이 부른다) — 가장 최근 귀속 월의 명세서, 없으면 empty. */
    @Transactional(readOnly = true)
    public Optional<LatestPayslip> latest(long companyId, long employeeId) {
        return jdbc.query("""
                        SELECT p.id, r.pay_month, p.net_pay
                        FROM paystub p JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = p.company_id
                        WHERE p.company_id = ? AND p.employee_id = ? ORDER BY r.pay_month DESC LIMIT 1
                        """,
                (rs, i) -> new LatestPayslip(rs.getLong("id"), rs.getString("pay_month"), rs.getLong("net_pay")),
                companyId, employeeId).stream().findFirst();
    }

    /**
     * 조직별 예산 대비 인건비(F-PAY-07). 예산이 있는 활성 조직마다 그 조직과 하위 조직(지금의 트리)에 정산 당시 소속이었던
     * 직원의 총지급 + 회사부담. 정산 전 월은 settled=false 이고 인건비 0. 집행률 = 인건비 ÷ 예산 × 100(소수 첫째 자리).
     */
    @Transactional(readOnly = true)
    public LaborCost laborCost(long companyId, YearMonth payMonth) {
        Long runId = jdbc.queryForList("SELECT id FROM payroll_run WHERE company_id = ? AND pay_month = ?",
                Long.class, companyId, payMonth.toString()).stream().findFirst().orElse(null);
        List<LaborCost.Row> rows = jdbc.query("""
                        WITH RECURSIVE tree AS (
                            SELECT id AS root_id, id FROM org_unit
                            WHERE company_id = ? AND is_active AND monthly_budget IS NOT NULL
                            UNION ALL
                            SELECT t.root_id, c.id FROM org_unit c JOIN tree t ON c.parent_id = t.id WHERE c.company_id = ?
                        )
                        SELECT o.id, o.name, o.monthly_budget,
                               COALESCE(sum(p.gross_pay + p.company_burden_total), 0) AS labor_cost
                        FROM org_unit o
                        JOIN tree t ON t.root_id = o.id
                        LEFT JOIN paystub p ON p.company_id = o.company_id AND p.org_unit_id_snap = t.id
                                           AND p.payroll_run_id = ?
                        WHERE o.company_id = ?
                        GROUP BY o.id, o.name, o.monthly_budget, o.sort_order
                        ORDER BY o.sort_order, o.id
                        """,
                (rs, i) -> {
                    long budget = rs.getLong("monthly_budget");
                    long cost = rs.getLong("labor_cost");
                    BigDecimal rate = budget == 0 ? null : BigDecimal.valueOf(cost).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(budget), 1, RoundingMode.HALF_UP);
                    return new LaborCost.Row(rs.getLong("id"), rs.getString("name"), budget, cost, rate);
                },
                companyId, companyId, runId == null ? -1L : runId, companyId);
        return new LaborCost(payMonth.toString(), runId != null, rows);
    }

    // ------------------------------------------------------------------

    private PayslipView view(long companyId, long id) {
        List<PayslipLine> lines = jdbc.query("""
                        SELECT pay_item_id, item_name_snap, item_kind::text AS item_kind, calc_method::text AS calc_method,
                               amount, taxable_amount, non_taxable_amount, company_amount, quantity, unit_price, formula_note
                        FROM paystub_line WHERE company_id = ? AND paystub_id = ? ORDER BY sort_order, id
                        """,
                (rs, i) -> new PayslipLine(rs.getLong("pay_item_id"), rs.getString("item_name_snap"),
                        PayItemKind.valueOf(rs.getString("item_kind")), PayCalcMethod.valueOf(rs.getString("calc_method")),
                        rs.getLong("amount"), rs.getLong("taxable_amount"), rs.getLong("non_taxable_amount"),
                        Optional.ofNullable(rs.getObject("company_amount", Long.class)), rs.getBigDecimal("quantity"),
                        rs.getObject("unit_price", Long.class), rs.getString("formula_note")),
                companyId, id);
        List<Long> claims = jdbc.queryForList(
                "SELECT id FROM expense_claim WHERE company_id = ? AND paystub_id = ? ORDER BY id", Long.class,
                companyId, id);
        return jdbc.query("""
                                SELECT p.*, r.pay_month, r.pay_date, r.status::text AS run_status, r.company_name_snap,
                                       r.business_reg_no_snap, r.ceo_name_snap, r.company_address_snap
                                FROM paystub p JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = p.company_id
                                WHERE p.company_id = ? AND p.id = ?
                                """,
                        (rs, i) -> new PayslipView(rs.getLong("id"), rs.getLong("payroll_run_id"),
                                rs.getString("pay_month"), rs.getObject("pay_date", LocalDate.class),
                                PayrollStatus.valueOf(rs.getString("run_status")),
                                new PayslipView.CompanySnap(rs.getString("company_name_snap"),
                                        rs.getString("business_reg_no_snap"), rs.getString("ceo_name_snap"),
                                        rs.getString("company_address_snap")),
                                rs.getLong("employee_id"), rs.getString("employee_no_snap"),
                                rs.getString("employee_name_snap"), rs.getString("org_name_snap"),
                                rs.getString("bank_account_masked_snap"), rs.getLong("base_pay"),
                                rs.getLong("ordinary_hourly_wage"), rs.getInt("worked_days"), rs.getInt("month_days"),
                                rs.getInt("dependents_count"), rs.getInt("children_count"), lines,
                                rs.getLong("gross_pay"), rs.getLong("taxable_pay"), rs.getLong("income_tax"),
                                rs.getLong("local_income_tax"), rs.getLong("total_deduction"), rs.getLong("net_pay"),
                                Optional.of(rs.getLong("company_burden_total")), claims),
                        companyId, id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
