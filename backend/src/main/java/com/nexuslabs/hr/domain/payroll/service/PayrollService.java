package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceDay;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceSummary;
import com.nexuslabs.hr.domain.attendance.dto.MonthlyAttendance;
import com.nexuslabs.hr.domain.attendance.service.AttendanceCalculator;
import com.nexuslabs.hr.domain.attendance.service.AttendanceDayStatus;
import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.payroll.dto.EmployeePayroll;
import com.nexuslabs.hr.domain.payroll.dto.PayrollPreview;
import com.nexuslabs.hr.domain.payroll.dto.PayrollRunRequest;
import com.nexuslabs.hr.domain.payroll.dto.PayrollRunRow;
import com.nexuslabs.hr.domain.payroll.dto.PayrollRunView;
import com.nexuslabs.hr.domain.payroll.dto.PayrollTotals;
import com.nexuslabs.hr.domain.payroll.dto.PayslipLine;
import com.nexuslabs.hr.domain.payroll.dto.TaxBrackets;
import com.nexuslabs.hr.domain.payroll.entity.AttendanceBasis;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import com.nexuslabs.hr.domain.payroll.entity.PayVarCode;
import com.nexuslabs.hr.domain.payroll.entity.PayrollRun;
import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;
import com.nexuslabs.hr.domain.payroll.entity.Paystub;
import com.nexuslabs.hr.domain.payroll.entity.PaystubLine;
import com.nexuslabs.hr.domain.payroll.repository.PayItemRepository;
import com.nexuslabs.hr.domain.payroll.repository.PayrollRunRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 월 급여 정산(F-PAY-05, BR-PAY-004–012). 대상 직원 · 급여 · 항목 · 근태 · 출장 경비 · 가족 수를 모아
 * {@link PayrollCalculator}(공통 컴포넌트)로 계산한다 — 미리보기와 확정이 같은 계산을 쓴다.
 * 확정은 귀속 월이 끝난 뒤에만, 전 직원을 한 트랜잭션으로 저장한다(역할 분담 v2 2.3 G).
 */
@Service
public class PayrollService {

    static final String MONTH_NOT_ENDED = "PAY_MONTH_NOT_ENDED";
    private static final String SALARY_MISSING = "PAY_SALARY_MISSING";

    private final PayItemRepository payItemRepository;
    private final PayrollRunRepository runRepository;
    private final PayVariableService payVariableService;
    private final TaxBracketService taxBracketService;
    private final AttendanceCalculator attendanceCalculator;
    private final WorkCalendar workCalendar;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public PayrollService(PayItemRepository payItemRepository, PayrollRunRepository runRepository,
                          PayVariableService payVariableService, TaxBracketService taxBracketService,
                          AttendanceCalculator attendanceCalculator, WorkCalendar workCalendar, JdbcTemplate jdbc,
                          EntityManager em, AuditLogger auditLogger, Clock clock) {
        this.payItemRepository = payItemRepository;
        this.runRepository = runRepository;
        this.payVariableService = payVariableService;
        this.taxBracketService = taxBracketService;
        this.attendanceCalculator = attendanceCalculator;
        this.workCalendar = workCalendar;
        this.jdbc = jdbc;
        this.em = em;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** 저장하지 않는 계산. 정산된 월 → PAY_MONTH_SETTLED, 월이 끝나기 전이면 경고만. */
    @Transactional(readOnly = true)
    public PayrollPreview preview(LoginUser user, PayrollRunRequest request) {
        long cid = user.companyId();
        YearMonth month = request.payMonth();
        requireNotSettled(cid, month);
        Computation c = compute(cid, month, request.manualInputs());
        return new PayrollPreview(month.toString(), payDate(cid, month, request.payDate()),
                ended(month) ? null : MONTH_NOT_ENDED, c.employees().stream().map(Calc::toView).toList(),
                c.unregistered(), totals(c.employees().stream().map(Calc::result).toList()));
    }

    /**
     * 확정 — 미리보기와 같은 입력으로 다시 계산해 정산 1행 · 명세서 · 줄을 저장하고 반영한 출장 경비에 명세서를 연결한다.
     * 귀속 월이 끝나기 전 → INVALID_STATE, 정산된 월 → PAY_MONTH_SETTLED, 대상 0명 → BUSINESS_RULE_VIOLATION. 감사 로그(EXECUTE).
     */
    @Transactional
    public PayrollRunView confirm(LoginUser user, PayrollRunRequest request) {
        long cid = user.companyId();
        YearMonth month = request.payMonth();
        if (!ended(month)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "귀속 월이 끝난 뒤에 확정할 수 있습니다");
        }
        // 같은 회사의 확정을 한 줄로 세운다 — 동시에 두 번 눌러도 유일 제약(payroll_run)이 마지막으로 막는다
        record CompanySnap(String name, String ceo, String regNo, String address) {
        }
        CompanySnap company = jdbc.queryForObject(
                "SELECT name, ceo_name, business_reg_no, address FROM company WHERE id = ? FOR UPDATE",
                (rs, i) -> new CompanySnap(rs.getString("name"), rs.getString("ceo_name"),
                        rs.getString("business_reg_no"), rs.getString("address")), cid);
        requireNotSettled(cid, month);
        Computation c = compute(cid, month, request.manualInputs());
        if (c.employees().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "정산할 직원이 없습니다(급여 등록 확인)");
        }

        PayrollRun run = runRepository.save(new PayrollRun(month.toString(), payDate(cid, month, request.payDate()),
                company.name(), company.ceo(), company.regNo(), company.address(), user.employeeId(),
                OffsetDateTime.now(clock)));
        for (Calc calc : c.employees()) {
            PayrollCalculator.Result r = calc.result();
            Paystub stub = new Paystub(run, em.getReference(Employee.class, calc.target().id()), calc.target().no(),
                    calc.target().name(), calc.target().orgUnitId(), calc.target().orgName(), calc.target().bankAccount(),
                    r.basePay(), r.ordinaryHourlyWage(), (short) calc.dependents(), (short) calc.children(),
                    (short) calc.target().workedDays(), (short) month.lengthOfMonth(), r.grossPay(), r.taxablePay(),
                    r.incomeTax(), r.localIncomeTax(), r.totalDeduction(), r.netPay(), r.companyBurdenTotal());
            for (PayrollCalculator.Line l : r.lines()) {
                stub.addLine(new PaystubLine(stub, em.getReference(PayItem.class, l.payItemId()), l.name(),
                        l.itemKind(), l.calcMethod(), l.amount(), l.taxableAmount(), l.nonTaxableAmount(),
                        l.companyAmount(), l.quantity(), l.unitPrice(), l.formulaNote(), l.sortOrder()));
            }
            em.persist(stub);
            em.flush();
            if (!calc.expenseClaimIds().isEmpty()) {
                jdbc.update("""
                                UPDATE expense_claim SET paystub_id = ?, updated_at = now()
                                WHERE company_id = ? AND id = ANY(?) AND status = 'APPROVED' AND paystub_id IS NULL
                                """,
                        stub.getId(), cid, calc.expenseClaimIds().toArray(Long[]::new));
            }
        }
        PayrollRunView view = view(cid, run.getId());
        auditLogger.log(user, AuditAction.EXECUTE, "PAYROLL_RUN", run.getId(), null,
                Map.of("payMonth", view.payMonth(), "payDate", view.payDate().toString(), "totals", view.totals()));
        return view;
    }

    /** 확정 → 지급완료(처리자 · 일시). 이미 지급완료 → INVALID_STATE. 감사 로그. */
    @Transactional
    public PayrollRunView markPaid(LoginUser user, long runId) {
        PayrollRun run = runRepository.findById(runId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (run.getStatus() != PayrollStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 지급완료 처리된 정산입니다");
        }
        run.markPaid(user.employeeId(), OffsetDateTime.now(clock));
        runRepository.flush();
        auditLogger.log(user, AuditAction.UPDATE, "PAYROLL_RUN", runId, Map.of("status", "CONFIRMED"),
                Map.of("status", "PAID"));
        return view(user.companyId(), runId);
    }

    /** 정산 월 목록 — 최근 귀속 월부터, 합계는 명세서를 더한 값. */
    @Transactional(readOnly = true)
    public List<PayrollRunRow> list(long companyId) {
        return jdbc.query(RUN_SELECT + " WHERE r.company_id = ? GROUP BY r.id ORDER BY r.pay_month DESC",
                (rs, i) -> new PayrollRunRow(rs.getLong("id"), rs.getString("pay_month"),
                        rs.getObject("pay_date", LocalDate.class), PayrollStatus.valueOf(rs.getString("status")),
                        totalsOf(rs)),
                companyId);
    }

    @Transactional(readOnly = true)
    public PayrollRunView view(long companyId, long runId) {
        List<PayrollRunView.PaystubSummary> stubs = jdbc.query("""
                        SELECT id, employee_id, employee_no_snap, employee_name_snap, org_name_snap, gross_pay,
                               total_deduction, net_pay, company_burden_total
                        FROM paystub WHERE company_id = ? AND payroll_run_id = ? ORDER BY employee_no_snap, id
                        """,
                (rs, i) -> new PayrollRunView.PaystubSummary(rs.getLong("id"), rs.getLong("employee_id"),
                        rs.getString("employee_no_snap"), rs.getString("employee_name_snap"),
                        rs.getString("org_name_snap"), rs.getLong("gross_pay"), rs.getLong("total_deduction"),
                        rs.getLong("net_pay"), rs.getLong("company_burden_total")),
                companyId, runId);
        return jdbc.query(RUN_VIEW_SELECT + " WHERE r.company_id = ? AND r.id = ? GROUP BY r.id, cb.name, pb.name",
                        (rs, i) -> new PayrollRunView(rs.getLong("id"), rs.getString("pay_month"),
                                rs.getObject("pay_date", LocalDate.class), PayrollStatus.valueOf(rs.getString("status")),
                                seoul(rs.getObject("confirmed_at", OffsetDateTime.class)),
                                rs.getString("confirmed_by_name"), seoul(rs.getObject("paid_at", OffsetDateTime.class)),
                                rs.getString("paid_by_name"), totalsOf(rs), stubs),
                        companyId, runId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    // ------------------------------------------------------------------ 계산 입력 모으기

    private record Target(long id, String no, String name, long orgUnitId, String orgName, String bankAccount,
                          int workedDays) {
    }

    private record Calc(Target target, PayrollCalculator.Result result, AttendanceSummary summary, int absenceMinutes,
                        int dependents, int children, List<Long> expenseClaimIds, int monthDays) {

        EmployeePayroll toView() {
            return new EmployeePayroll(target.id(), target.no(), target.name(), target.orgName(),
                    new EmployeePayroll.Attendance(summary.overtimeMinutes(), summary.nightMinutes(),
                            summary.holidayMinutes(), summary.holidayOvertimeMinutes(), summary.absentDays(),
                            absenceMinutes, summary.lateCount()),
                    result.basePay(), result.ordinaryHourlyWage(), target.workedDays(), monthDays,
                    result.lines().stream().map(PayrollService::line).toList(), result.grossPay(), result.taxablePay(),
                    dependents, children, result.incomeTax(), result.localIncomeTax(), result.totalDeduction(),
                    result.netPay(), result.companyBurdenTotal(), expenseClaimIds);
        }
    }

    private record Computation(List<Calc> employees, List<PayrollPreview.Unregistered> unregistered) {
    }

    private Computation compute(long companyId, YearMonth month, List<PayrollRunRequest.ManualInput> manualInputs) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        List<Target> targets = targets(companyId, first, last);
        Map<Long, Long> monthlyBase = monthlyBases(companyId, targets.stream().map(Target::id).toList(), last);

        List<Target> registered = new ArrayList<>();
        List<PayrollPreview.Unregistered> unregistered = new ArrayList<>();
        for (Target t : targets) {
            if (monthlyBase.containsKey(t.id())) {
                registered.add(t);
            } else {
                unregistered.add(new PayrollPreview.Unregistered(t.id(), t.no(), t.name(), SALARY_MISSING));
            }
        }
        List<Long> ids = registered.stream().map(Target::id).toList();

        List<PayItem> active = payItemRepository.findAll().stream().filter(PayItem::isActive).toList();
        Map<Long, Map<Long, Long>> manual = manualAmounts(manualInputs, active, new HashSet<>(ids));
        Map<Long, Map<Long, Long>> selected = selectedAmounts(companyId, ids, last);
        Map<Long, MonthlyAttendance> attendance = attendanceCalculator.months(companyId, ids, month);
        WorkCalendar.Snapshot calendar = workCalendar.snapshot(companyId);
        boolean tripItem = active.stream().anyMatch(i -> i.getCalcMethod() == PayCalcMethod.TRIP_EXPENSE);
        Map<Long, List<long[]>> claims = tripItem ? expenseClaims(companyId, ids) : Map.of();
        Map<Long, int[]> family = familyCounts(companyId, ids);

        TaxBrackets brackets = taxBracketService.get(companyId);
        PayrollCalculator.Settings settings = new PayrollCalculator.Settings(
                active.stream().map(PayrollService::item).toList(),
                payVariableService.valueOn(companyId, PayVarCode.MONTHLY_STANDARD_HOURS, last),
                payVariableService.valueOn(companyId, PayVarCode.DEPENDENT_DEDUCTION, last).longValue(),
                payVariableService.valueOn(companyId, PayVarCode.MULTI_CHILD_DEDUCTION, last).longValue(),
                payVariableService.valueOn(companyId, PayVarCode.LOCAL_TAX_RATE, last),
                brackets.brackets().stream().map(b -> new PayrollCalculator.Bracket(b.lowerBound(), b.upperBound(),
                        b.rate(), b.progressiveDeduction())).toList());

        List<Calc> calcs = new ArrayList<>();
        for (Target t : registered) {
            MonthlyAttendance m = attendance.get(t.id());
            AttendanceSummary s = m.summary();
            int absenceMinutes = 0;
            for (AttendanceDay d : m.days()) {
                if (d.status() == AttendanceDayStatus.ABSENT) {
                    absenceMinutes += calendar.scheduleOn(d.date()).dailyStandardMinutes();
                }
            }
            Map<AttendanceBasis, Integer> minutes = new EnumMap<>(AttendanceBasis.class);
            minutes.put(AttendanceBasis.OVERTIME, s.overtimeMinutes());
            minutes.put(AttendanceBasis.NIGHT, s.nightMinutes());
            minutes.put(AttendanceBasis.HOLIDAY, s.holidayMinutes());
            minutes.put(AttendanceBasis.HOLIDAY_OVERTIME, s.holidayOvertimeMinutes());
            minutes.put(AttendanceBasis.ABSENCE, absenceMinutes);
            List<long[]> mine = claims.getOrDefault(t.id(), List.of());
            int[] fam = family.getOrDefault(t.id(), new int[]{0, 0});
            PayrollCalculator.Input input = new PayrollCalculator.Input(monthlyBase.get(t.id()), t.workedDays(),
                    month.lengthOfMonth(), selected.getOrDefault(t.id(), Map.of()), manual.getOrDefault(t.id(), Map.of()),
                    minutes, s.absentDays(), mine.stream().mapToLong(c -> c[1]).sum(), fam[0], fam[1]);
            calcs.add(new Calc(t, PayrollCalculator.calculate(settings, input), s, absenceMinutes, fam[0], fam[1],
                    mine.stream().map(c -> c[0]).toList(), month.lengthOfMonth()));
        }
        return new Computation(calcs, unregistered);
    }

    /**
     * 급여 대상이고 귀속 월에 하루라도 재직한 직원(사원번호 순). 재직 일수는 "발효일부터 그 상태" — 퇴직 발효일은 재직일이 아니다
     * (역할 분담 v2 2.3 22번). 휴직은 일할하지 않는다(2.3 G 45).
     */
    private List<Target> targets(long companyId, LocalDate first, LocalDate last) {
        return jdbc.query("""
                        SELECT e.id, e.employee_no, e.name, e.org_unit_id, o.name AS org_name, e.hire_date,
                               e.bank_name, e.bank_account_last4,
                               (SELECT min(h.effective_date) FROM employment_status_history h
                                WHERE h.company_id = e.company_id AND h.employee_id = e.id AND h.status = 'RESIGNED')
                                   AS resigned_from
                        FROM employee e
                        JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                        WHERE e.company_id = ? AND e.payroll_eligible AND e.hire_date <= ?
                          AND NOT EXISTS (SELECT 1 FROM employment_status_history h
                                          WHERE h.company_id = e.company_id AND h.employee_id = e.id
                                            AND h.status = 'RESIGNED' AND h.effective_date <= ?)
                        ORDER BY e.employee_no, e.id
                        """,
                (rs, i) -> {
                    LocalDate from = max(rs.getObject("hire_date", LocalDate.class), first);
                    LocalDate resigned = rs.getObject("resigned_from", LocalDate.class);
                    LocalDate to = resigned == null || resigned.isAfter(last) ? last : resigned.minusDays(1);
                    String last4 = rs.getString("bank_account_last4");
                    String bank = last4 == null ? null
                            : (rs.getString("bank_name") == null ? "" : rs.getString("bank_name") + " ") + "***-****-" + last4;
                    return new Target(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                            rs.getLong("org_unit_id"), rs.getString("org_name"), bank,
                            (int) ChronoUnit.DAYS.between(from, to) + 1);
                },
                companyId, Date.valueOf(last), Date.valueOf(first));
    }

    /** 직원 → 그날 유효한 월 기본급(같은 적용 시작일이면 나중 행). 없으면 급여 미등록. */
    private Map<Long, Long> monthlyBases(long companyId, List<Long> ids, LocalDate date) {
        Map<Long, Long> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        SELECT DISTINCT ON (employee_id) employee_id, monthly_base FROM employee_salary
                        WHERE company_id = ? AND employee_id = ANY(?) AND effective_from <= ?
                        ORDER BY employee_id, effective_from DESC, created_at DESC, id DESC
                        """,
                rs -> {
                    result.put(rs.getLong("employee_id"), rs.getLong("monthly_base"));
                },
                companyId, ids.toArray(Long[]::new), Date.valueOf(date));
        return result;
    }

    /** 직원 → (지정 직원 항목 → 그날 유효한 금액). */
    private Map<Long, Map<Long, Long>> selectedAmounts(long companyId, List<Long> ids, LocalDate date) {
        Map<Long, Map<Long, Long>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        SELECT DISTINCT ON (employee_id, pay_item_id) employee_id, pay_item_id, amount
                        FROM employee_pay_item
                        WHERE company_id = ? AND employee_id = ANY(?) AND effective_from <= ?
                        ORDER BY employee_id, pay_item_id, effective_from DESC, created_at DESC, id DESC
                        """,
                rs -> {
                    result.computeIfAbsent(rs.getLong("employee_id"), k -> new HashMap<>())
                            .put(rs.getLong("pay_item_id"), rs.getLong("amount"));
                },
                companyId, ids.toArray(Long[]::new), Date.valueOf(date));
        return result;
    }

    /** 직원 → [청구 ID, 합계] — 승인됐고 아직 정산에 반영되지 않은 출장 경비(BR-PAY-012). */
    private Map<Long, List<long[]>> expenseClaims(long companyId, List<Long> ids) {
        Map<Long, List<long[]>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        SELECT c.id, c.employee_id, COALESCE(sum(l.amount), 0) AS total
                        FROM expense_claim c
                        JOIN expense_claim_line l ON l.expense_claim_id = c.id AND l.company_id = c.company_id
                        WHERE c.company_id = ? AND c.employee_id = ANY(?) AND c.status = 'APPROVED' AND c.paystub_id IS NULL
                        GROUP BY c.id, c.employee_id ORDER BY c.id
                        """,
                rs -> {
                    result.computeIfAbsent(rs.getLong("employee_id"), k -> new ArrayList<>())
                            .add(new long[]{rs.getLong("id"), rs.getLong("total")});
                },
                companyId, ids.toArray(Long[]::new));
        return result;
    }

    /** 직원 → [부양가족 수, 자녀 수] — 공제 대상 가족, 공제 대상인 자녀(F-EMP-09, BR-EMP-007). */
    private Map<Long, int[]> familyCounts(long companyId, List<Long> ids) {
        Map<Long, int[]> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        SELECT employee_id, count(*) FILTER (WHERE is_tax_dependent) AS dependents,
                               count(*) FILTER (WHERE is_tax_dependent AND relation = 'CHILD') AS children
                        FROM employee_family WHERE company_id = ? AND employee_id = ANY(?) GROUP BY employee_id
                        """,
                rs -> {
                    result.put(rs.getLong("employee_id"), new int[]{rs.getInt("dependents"), rs.getInt("children")});
                },
                companyId, ids.toArray(Long[]::new));
        return result;
    }

    /** 수동 입력 검증(2.3 G 47) — 활성 수동 항목 · 정산 대상 · 같은 직원 · 항목 한 번. 직원 → (항목 → 금액). */
    private static Map<Long, Map<Long, Long>> manualAmounts(List<PayrollRunRequest.ManualInput> inputs,
                                                            List<PayItem> active, Set<Long> targetIds) {
        Map<Long, Map<Long, Long>> result = new HashMap<>();
        if (inputs == null) {
            return result;
        }
        Set<Long> manualItems = new HashSet<>();
        active.stream().filter(i -> i.getCalcMethod() == PayCalcMethod.MANUAL).forEach(i -> manualItems.add(i.getId()));
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            PayrollRunRequest.ManualInput m = inputs.get(i);
            if (!manualItems.contains(m.payItemId())) {
                errors.put("manualInputs[%d].payItemId".formatted(i), "활성인 수동 입력 항목이 아닙니다");
            } else if (!targetIds.contains(m.employeeId())) {
                errors.put("manualInputs[%d].employeeId".formatted(i), "이 달 정산 대상(급여 등록된 직원)이 아닙니다");
            } else if (result.computeIfAbsent(m.employeeId(), k -> new HashMap<>())
                    .putIfAbsent(m.payItemId(), m.amount()) != null) {
                errors.put("manualInputs[%d]".formatted(i), "같은 직원 · 항목을 두 번 보냈습니다");
            }
        }
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        return result;
    }

    // ------------------------------------------------------------------

    private void requireNotSettled(long companyId, YearMonth month) {
        Boolean settled = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payroll_run WHERE company_id = ? AND pay_month = ?)",
                Boolean.class, companyId, month.toString());
        if (Boolean.TRUE.equals(settled)) {
            throw new BusinessException(ErrorCode.PAY_MONTH_SETTLED);
        }
    }

    /** 귀속 월 다음 달의 회사 기본 급여 지급일, 그달 말일보다 크면 말일(2.3 G 44). */
    private LocalDate payDate(long companyId, YearMonth month, LocalDate requested) {
        if (requested != null) {
            return requested;
        }
        int payDay = jdbc.queryForObject("SELECT pay_day FROM company WHERE id = ?", Integer.class, companyId);
        YearMonth next = month.plusMonths(1);
        return next.atDay(Math.min(payDay, next.lengthOfMonth()));
    }

    /** 오늘이 귀속 월 말일 뒤인가. */
    private boolean ended(YearMonth month) {
        return LocalDate.now(clock).isAfter(month.atEndOfMonth());
    }

    private static PayrollTotals totals(List<PayrollCalculator.Result> results) {
        return new PayrollTotals(results.size(), results.stream().mapToLong(PayrollCalculator.Result::grossPay).sum(),
                results.stream().mapToLong(PayrollCalculator.Result::totalDeduction).sum(),
                results.stream().mapToLong(PayrollCalculator.Result::netPay).sum(),
                results.stream().mapToLong(PayrollCalculator.Result::companyBurdenTotal).sum());
    }

    private static PayrollTotals totalsOf(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PayrollTotals(rs.getInt("headcount"), rs.getLong("gross_pay"), rs.getLong("total_deduction"),
                rs.getLong("net_pay"), rs.getLong("company_burden_total"));
    }

    static PayslipLine line(PayrollCalculator.Line l) {
        return new PayslipLine(l.payItemId(), l.name(), l.itemKind(), l.calcMethod(), l.amount(), l.taxableAmount(),
                l.nonTaxableAmount(), Optional.ofNullable(l.companyAmount()), l.quantity(), l.unitPrice(),
                l.formulaNote());
    }

    private static PayrollCalculator.Item item(PayItem i) {
        return new PayrollCalculator.Item(i.getId(), i.getName(), i.getItemKind(), i.getCalcMethod(), i.getApplyTo(),
                i.isTaxable(), i.getNonTaxableLimit(), i.getDefaultAmount(), i.getBaseRate(), i.getEmployeeRate(),
                i.getCompanyRate(), i.getBaseUpperLimit(), i.getBaseLowerLimit(), i.getAttendanceBasis(),
                i.getMultiplier(), i.isInOrdinaryWage(), i.getSortOrder());
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }

    private static final String RUN_SELECT = """
            SELECT r.id, r.pay_month, r.pay_date, r.status::text AS status, count(p.id) AS headcount,
                   COALESCE(sum(p.gross_pay), 0) AS gross_pay, COALESCE(sum(p.total_deduction), 0) AS total_deduction,
                   COALESCE(sum(p.net_pay), 0) AS net_pay, COALESCE(sum(p.company_burden_total), 0) AS company_burden_total
            FROM payroll_run r LEFT JOIN paystub p ON p.payroll_run_id = r.id AND p.company_id = r.company_id
            """;

    private static final String RUN_VIEW_SELECT = """
            SELECT r.id, r.pay_month, r.pay_date, r.status::text AS status, r.confirmed_at, r.paid_at,
                   cb.name AS confirmed_by_name, pb.name AS paid_by_name, count(p.id) AS headcount,
                   COALESCE(sum(p.gross_pay), 0) AS gross_pay, COALESCE(sum(p.total_deduction), 0) AS total_deduction,
                   COALESCE(sum(p.net_pay), 0) AS net_pay, COALESCE(sum(p.company_burden_total), 0) AS company_burden_total
            FROM payroll_run r
            LEFT JOIN paystub p ON p.payroll_run_id = r.id AND p.company_id = r.company_id
            LEFT JOIN employee cb ON cb.id = r.confirmed_by AND cb.company_id = r.company_id
            LEFT JOIN employee pb ON pb.id = r.paid_by AND pb.company_id = r.company_id
            """;
}
