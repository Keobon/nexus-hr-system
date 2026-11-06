package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.payroll.dto.EmployeePayItemRequest;
import com.nexuslabs.hr.domain.payroll.dto.EmployeeSalaries;
import com.nexuslabs.hr.domain.payroll.dto.PayItemAssigned;
import com.nexuslabs.hr.domain.payroll.dto.SalaryRegistered;
import com.nexuslabs.hr.domain.payroll.dto.SalaryRequest;
import com.nexuslabs.hr.domain.payroll.dto.SalaryRow;
import com.nexuslabs.hr.domain.payroll.entity.EmployeePayItem;
import com.nexuslabs.hr.domain.payroll.entity.EmployeeSalary;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;
import com.nexuslabs.hr.domain.payroll.entity.PayVarCode;
import com.nexuslabs.hr.domain.payroll.entity.SalaryType;
import com.nexuslabs.hr.domain.payroll.repository.EmployeePayItemRepository;
import com.nexuslabs.hr.domain.payroll.repository.EmployeeSalaryRepository;
import com.nexuslabs.hr.domain.payroll.repository.PayItemRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 직원 급여 등록 · 조회(F-PAY-03·04, BR-PAY-002·003·009). 기본 급여와 직원별 항목은 append-only 이력이다 —
 * 정정은 새 행으로 하고, 같은 적용 시작일이 여럿이면 나중에 만든 행이 정정본이다(ERD EMPLOYEE_SALARY).
 * 등록은 감사 로그를 남긴다(BR-AUDIT-001). 급여에는 팀 범위가 없다(BR-PAY-001).
 */
@Service
public class SalaryService {

    private static final String SETTLED_WARNING = "PAY_MONTH_SETTLED";

    private final EmployeeSalaryRepository salaryRepository;
    private final EmployeePayItemRepository employeePayItemRepository;
    private final PayItemRepository payItemRepository;
    private final PayVariableService payVariableService;
    private final PayItemAmountResolver amountResolver;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public SalaryService(EmployeeSalaryRepository salaryRepository, EmployeePayItemRepository employeePayItemRepository,
                         PayItemRepository payItemRepository, PayVariableService payVariableService,
                         PayItemAmountResolver amountResolver, JdbcTemplate jdbc,
                         EntityManager em, AuditLogger auditLogger, Clock clock) {
        this.salaryRepository = salaryRepository;
        this.employeePayItemRepository = employeePayItemRepository;
        this.payItemRepository = payItemRepository;
        this.payVariableService = payVariableService;
        this.amountResolver = amountResolver;
        this.jdbc = jdbc;
        this.em = em;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /**
     * 기본 급여 등록. 연봉제는 연봉만 받고 월 기본급 = ⌊연봉 ÷ 적용 시작일에 유효한 연봉 분할 개월 수⌋, 월급제는 월 기본급만.
     * 급여 대상 아님 → BUSINESS_RULE_VIOLATION, 퇴직자 → EMPLOYEE_RESIGNED, 적용 시작일 < 입사일 → VALIDATION_ERROR.
     */
    @Transactional
    public SalaryRegistered register(LoginUser user, long employeeId, SalaryRequest request) {
        long cid = user.companyId();
        requireTarget(cid, employeeId, request.effectiveFrom());
        Long annual = null;
        long monthly;
        if (request.salaryType() == SalaryType.ANNUAL) {
            annual = request.annualSalary();
            if (annual == null || annual <= 0) {
                throw BusinessException.invalidFields(Map.of("annualSalary", "연봉은 0보다 커야 합니다"));
            }
            BigDecimal months = payVariableService.valueOn(cid, PayVarCode.ANNUAL_SPLIT_MONTHS, request.effectiveFrom());
            monthly = BigDecimal.valueOf(annual).divide(months, 0, RoundingMode.FLOOR).longValueExact();
            if (monthly <= 0) {
                throw BusinessException.invalidFields(Map.of("annualSalary", "연봉을 분할 개월 수로 나눈 월 기본급이 0보다 커야 합니다"));
            }
        } else {
            if (request.monthlyBase() == null || request.monthlyBase() <= 0) {
                throw BusinessException.invalidFields(Map.of("monthlyBase", "월 기본급은 0보다 커야 합니다"));
            }
            monthly = request.monthlyBase();
        }
        EmployeeSalary saved = salaryRepository.saveAndFlush(new EmployeeSalary(em.getReference(Employee.class, employeeId),
                request.salaryType(), annual, monthly, request.effectiveFrom(), request.reason().trim(),
                user.employeeId()));
        EmployeeSalaries.SalaryEntry entry = salaryEntries(cid, employeeId).stream()
                .filter(e -> e.id() == saved.getId()).findFirst().orElseThrow();
        auditLogger.log(user, AuditAction.CREATE, "EMPLOYEE_SALARY", saved.getId(), null,
                Map.of("employeeId", employeeId, "salaryType", entry.salaryType().name(),
                        "annualSalary", annual == null ? "" : annual, "monthlyBase", monthly,
                        "effectiveFrom", entry.effectiveFrom().toString(), "reason", entry.reason()));
        return new SalaryRegistered(entry.id(), entry.salaryType(), entry.annualSalary(), entry.monthlyBase(),
                entry.effectiveFrom(), entry.reason(), entry.createdByName(), entry.createdAt(),
                settledWarning(jdbc, cid, request.effectiveFrom()));
    }

    /**
     * 직원별 항목 금액 등록 — 활성 고정액 항목(전원 · 지정 직원 모두, 2026-11-06). 0 = 받지 않음, null = 개별 금액 해제
     * (부서 · 직급 규칙 · 기본 금액을 따른다). 대상 직원 규칙은 기본 급여와 같다.
     */
    @Transactional
    public PayItemAssigned assignItem(LoginUser user, long employeeId, EmployeePayItemRequest request) {
        long cid = user.companyId();
        PayItem item = payItemRepository.findById(request.payItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!item.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        if (item.getCalcMethod() != PayCalcMethod.FIXED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "고정액 항목만 직원별 금액을 등록합니다");
        }
        requireTarget(cid, employeeId, request.effectiveFrom());
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        EmployeePayItem saved = employeePayItemRepository.saveAndFlush(new EmployeePayItem(
                em.getReference(Employee.class, employeeId), item, request.amount(), request.effectiveFrom(), reason,
                user.employeeId()));
        EmployeeSalaries.PayItemEntry entry = payItemEntries(cid, employeeId).getOrDefault(item.getId(), List.of())
                .stream().filter(e -> e.id() == saved.getId()).findFirst().orElseThrow();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("employeeId", employeeId);
        after.put("payItemId", item.getId());
        after.put("amount", request.amount());
        after.put("effectiveFrom", request.effectiveFrom().toString());
        after.put("reason", reason);
        auditLogger.log(user, AuditAction.CREATE, "EMPLOYEE_PAY_ITEM", saved.getId(), null, after);
        return new PayItemAssigned(entry.id(), item.getId(), entry.amount(), entry.effectiveFrom(), entry.reason(),
                entry.createdByName(), entry.createdAt(), settledWarning(jdbc, cid, request.effectiveFrom()));
    }

    /** 한 직원의 급여(PAYROLL_READ). 다른 회사 직원은 404. */
    @Transactional(readOnly = true)
    public EmployeeSalaries of(long companyId, long employeeId) {
        boolean eligible = jdbc.queryForList("SELECT payroll_eligible FROM employee WHERE id = ? AND company_id = ?",
                        Boolean.class, employeeId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return build(companyId, employeeId, eligible);
    }

    /** 내 급여 — 급여 대상이 아니면 같은 모양으로 비어 있다. */
    @Transactional(readOnly = true)
    public EmployeeSalaries mine(LoginUser user) {
        EmployeeSalaries salaries = of(user.companyId(), user.employeeId());
        return salaries.payrollEligible() ? salaries
                : new EmployeeSalaries(user.employeeId(), false, null, List.of(), List.of());
    }

    /**
     * 직원 급여 목록(PAYROLL_READ) — 급여 대상인 재직 · 휴직 직원, 사원번호 순. 미등록 직원도 나온다(registered=false).
     * keyword 는 이름 · 사원번호, orgUnitId 는 하위 조직 포함.
     */
    @Transactional(readOnly = true)
    public PageImpl<SalaryRow> list(long companyId, Long orgUnitId, String keyword, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        StringBuilder where = new StringBuilder("""
                 WHERE e.company_id = ? AND e.payroll_eligible AND e.status IN ('ACTIVE', 'ON_LEAVE')
                """);
        List<Object> args = new ArrayList<>(List.of(companyId));
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.name ILIKE ? OR e.employee_no ILIKE ?)");
            String like = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
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
        String from = " FROM employee e JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id";
        long total = jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
        // LATERAL 안의 오늘이 WHERE 인자보다 앞에 온다
        List<Object> pageArgs = new ArrayList<>(List.of(Date.valueOf(today)));
        pageArgs.addAll(args);
        pageArgs.addAll(List.of(pageable.getPageSize(), pageable.getOffset()));
        record Base(long id, String no, String name, String org, Current current) {
        }
        List<Base> bases = jdbc.query("SELECT e.id, e.employee_no, e.name, o.name AS org_unit_name, s.*" + from
                        + " LEFT JOIN LATERAL (" + CURRENT_SALARY + ") s ON TRUE" + where
                        + " ORDER BY e.employee_no, e.id LIMIT ? OFFSET ?",
                (rs, i) -> new Base(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                        rs.getString("org_unit_name"), CURRENT_MAPPER.mapRow(rs, i)),
                pageArgs.toArray());
        List<PayItem> items = activeItems();
        Map<Long, Map<Long, PayItemAmountResolver.Resolved>> fixed = amountResolver.resolve(companyId,
                bases.stream().map(Base::id).toList(), items, today);
        List<SalaryRow> rows = bases.stream().map(b -> b.current() == null
                ? new SalaryRow(b.id(), b.no(), b.name(), b.org(), false, null, null, null, null, null)
                : new SalaryRow(b.id(), b.no(), b.name(), b.org(), true, b.current().type(), b.current().annual(),
                        b.current().monthly(), b.current().effectiveFrom(),
                        fixedEarnings(items, b.current().monthly(), fixed.getOrDefault(b.id(), Map.of()))))
                .toList();
        return new PageImpl<>(rows, pageable, total);
    }

    // ------------------------------------------------------------------

    /** 같은 회사 직원 · 급여 대상 · 퇴직 아님 · 적용 시작일 ≥ 입사일. */
    private void requireTarget(long companyId, long employeeId, LocalDate effectiveFrom) {
        record Target(String status, boolean eligible, LocalDate hireDate) {
        }
        Target t = jdbc.query("""
                                SELECT status::text AS status, payroll_eligible, hire_date FROM employee
                                WHERE id = ? AND company_id = ?
                                """,
                        (rs, i) -> new Target(rs.getString("status"), rs.getBoolean("payroll_eligible"),
                                rs.getObject("hire_date", LocalDate.class)),
                        employeeId, companyId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if ("RESIGNED".equals(t.status())) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        if (!t.eligible()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "급여 대상이 아닌 직원입니다");
        }
        if (effectiveFrom.isBefore(t.hireDate())) {
            throw BusinessException.invalidFields(Map.of("effectiveFrom", "적용 시작일은 입사일 이후여야 합니다"));
        }
    }

    /** 적용 시작일의 귀속 월이나 그 뒤의 월이 이미 정산됐으면 경고 — 그 명세서는 바뀌지 않는다(BR-PAY-005). 규칙 등록도 쓴다. */
    static String settledWarning(JdbcTemplate jdbc, long companyId, LocalDate effectiveFrom) {
        Boolean settled = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payroll_run WHERE company_id = ? AND pay_month >= ? AND status <> 'CANCELLED')",
                Boolean.class, companyId, YearMonth.from(effectiveFrom).toString());
        return Boolean.TRUE.equals(settled) ? SETTLED_WARNING : null;
    }

    private EmployeeSalaries build(long companyId, long employeeId, boolean eligible) {
        LocalDate today = LocalDate.now(clock);
        List<EmployeeSalaries.SalaryEntry> salaries = salaryEntries(companyId, employeeId);
        EmployeeSalaries.SalaryEntry current = salaries.stream()
                .filter(s -> !s.effectiveFrom().isAfter(today)).findFirst().orElse(null);
        Map<Long, List<EmployeeSalaries.PayItemEntry>> histories = payItemEntries(companyId, employeeId);

        List<PayItem> items = new ArrayList<>(payItemRepository.findAll());
        items.sort(Comparator.comparingInt(PayItem::getSortOrder).thenComparing(PayItem::getId));
        Map<Long, PayItemAmountResolver.Resolved> fixed = amountResolver.resolve(companyId, List.of(employeeId),
                items.stream().filter(PayItem::isActive).toList(), today).getOrDefault(employeeId, Map.of());
        List<EmployeeSalaries.PayItemLine> lines = new ArrayList<>();
        for (PayItem item : items) {
            List<EmployeeSalaries.PayItemEntry> history = histories.getOrDefault(item.getId(), List.of());
            if (item.getCalcMethod() == PayCalcMethod.FIXED) {
                PayItemAmountResolver.Resolved r = item.isActive() ? fixed.get(item.getId()) : null;
                // 전원 항목은 늘, 지정 직원 항목은 정해진 금액이나 개별 이력이 있을 때만(비활성 항목은 이력만)
                if (r != null || !history.isEmpty()) {
                    lines.add(line(item, r == null ? null : r.amount(), r == null ? null : r.source(), history));
                }
            } else if (item.isActive() && item.getCalcMethod() == PayCalcMethod.BASE_RATE) {
                lines.add(line(item, current == null ? null : baseRateAmount(current.monthlyBase(), item), null,
                        List.of()));
            }
        }
        return new EmployeeSalaries(employeeId, eligible, current == null ? null
                : new EmployeeSalaries.Current(current.salaryType(), current.annualSalary(), current.monthlyBase(),
                current.effectiveFrom()), salaries, lines);
    }

    private static EmployeeSalaries.PayItemLine line(PayItem item, Long amount, PayItemAmountResolver.Source source,
                                                     List<EmployeeSalaries.PayItemEntry> history) {
        return new EmployeeSalaries.PayItemLine(item.getId(), item.getName(), item.getItemKind(), item.getCalcMethod(),
                item.getApplyTo(), amount, source, history);
    }

    /** 최신순(적용 시작일 → 만든 시각). 첫 행 중 오늘 이전 것이 오늘 유효한 값이다. */
    private List<EmployeeSalaries.SalaryEntry> salaryEntries(long companyId, long employeeId) {
        return jdbc.query("""
                        SELECT s.id, s.salary_type::text AS salary_type, s.annual_salary, s.monthly_base, s.effective_from,
                               s.reason, c.name AS created_by_name, s.created_at
                        FROM employee_salary s
                        LEFT JOIN employee c ON c.id = s.created_by AND c.company_id = s.company_id
                        WHERE s.company_id = ? AND s.employee_id = ?
                        ORDER BY s.effective_from DESC, s.created_at DESC, s.id DESC
                        """,
                (rs, i) -> new EmployeeSalaries.SalaryEntry(rs.getLong("id"),
                        SalaryType.valueOf(rs.getString("salary_type")), rs.getObject("annual_salary", Long.class),
                        rs.getLong("monthly_base"), rs.getObject("effective_from", LocalDate.class),
                        rs.getString("reason"), rs.getString("created_by_name"),
                        seoul(rs.getObject("created_at", OffsetDateTime.class))),
                companyId, employeeId);
    }

    /** 항목 ID → 이력(최신순). */
    private Map<Long, List<EmployeeSalaries.PayItemEntry>> payItemEntries(long companyId, long employeeId) {
        Map<Long, List<EmployeeSalaries.PayItemEntry>> result = new HashMap<>();
        jdbc.query("""
                        SELECT p.id, p.pay_item_id, p.amount, p.effective_from, p.reason, c.name AS created_by_name, p.created_at
                        FROM employee_pay_item p
                        LEFT JOIN employee c ON c.id = p.created_by AND c.company_id = p.company_id
                        WHERE p.company_id = ? AND p.employee_id = ?
                        ORDER BY p.effective_from DESC, p.created_at DESC, p.id DESC
                        """,
                rs -> {
                    result.computeIfAbsent(rs.getLong("pay_item_id"), k -> new ArrayList<>())
                            .add(new EmployeeSalaries.PayItemEntry(rs.getLong("id"), rs.getObject("amount", Long.class),
                                    rs.getObject("effective_from", LocalDate.class), rs.getString("reason"),
                                    rs.getString("created_by_name"),
                                    seoul(rs.getObject("created_at", OffsetDateTime.class))));
                },
                companyId, employeeId);
        return result;
    }

    private List<PayItem> activeItems() {
        return payItemRepository.findAll().stream().filter(PayItem::isActive).toList();
    }

    /** 고정액 지급(직원에게 정해진 금액 — 개별 · 직급 · 부서 · 기본) + 기본급 대비 % 지급. 항목마다 원 미만 내림. */
    private static long fixedEarnings(List<PayItem> items, long monthlyBase,
                                      Map<Long, PayItemAmountResolver.Resolved> fixed) {
        long sum = 0;
        for (PayItem item : items) {
            if (item.getItemKind() != PayItemKind.EARNING) {
                continue;
            }
            if (item.getCalcMethod() == PayCalcMethod.FIXED) {
                PayItemAmountResolver.Resolved r = fixed.get(item.getId());
                sum += r == null ? 0 : r.amount();
            } else if (item.getCalcMethod() == PayCalcMethod.BASE_RATE) {
                sum += baseRateAmount(monthlyBase, item);
            }
        }
        return sum;
    }

    private static long baseRateAmount(long monthlyBase, PayItem item) {
        return BigDecimal.valueOf(monthlyBase).multiply(item.getBaseRate())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact();
    }

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }

    /** 그날 유효한 기본 급여 한 행(같은 적용 시작일이면 나중 행). */
    private static final String CURRENT_SALARY = """
            SELECT es.salary_type::text AS salary_type, es.annual_salary, es.monthly_base, es.effective_from
            FROM employee_salary es
            WHERE es.company_id = e.company_id AND es.employee_id = e.id AND es.effective_from <= ?
            ORDER BY es.effective_from DESC, es.created_at DESC, es.id DESC
            LIMIT 1
            """;

    private record Current(SalaryType type, Long annual, long monthly, LocalDate effectiveFrom) {
    }

    private static final RowMapper<Current> CURRENT_MAPPER = (rs, i) -> rs.getString("salary_type") == null ? null
            : new Current(SalaryType.valueOf(rs.getString("salary_type")), rs.getObject("annual_salary", Long.class),
            rs.getLong("monthly_base"), rs.getObject("effective_from", LocalDate.class));
}
