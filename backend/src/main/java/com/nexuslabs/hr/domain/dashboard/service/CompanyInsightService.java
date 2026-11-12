package com.nexuslabs.hr.domain.dashboard.service;

import com.nexuslabs.hr.domain.dashboard.dto.CompanyLaborCost;
import com.nexuslabs.hr.domain.dashboard.dto.CompanyPerformance;
import com.nexuslabs.hr.domain.evaluation.service.EvalScoreCalculator;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import com.nexuslabs.hr.global.permission.PermissionScope;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 홈 전사 위젯 — 성과 현황(F-DASH-06) · 직원별 인건비(F-DASH-07). 저장하지 않고 원천 데이터에서 바로 집계한다(BR-DASH-001).
 * 권한: DASHBOARD_COMPANY 에 더해 성과는 전사 EVAL_READ, 인건비는 PAYROLL_READ.
 */
@Service
public class CompanyInsightService {

    private static final int TOP = 5;
    private static final int HOME_LABOR_TOP = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);
    /** 수준 = 총점 ÷ 20(소수 첫째 자리) — 프론트 levelOf 와 같은 경계. */
    private static final List<Map.Entry<BigDecimal, String>> LEVELS = List.of(
            Map.entry(new BigDecimal("4.5"), "VERY_GOOD"), Map.entry(new BigDecimal("3.5"), "GOOD"),
            Map.entry(new BigDecimal("2.5"), "NORMAL"), Map.entry(new BigDecimal("1.5"), "POOR"),
            Map.entry(BigDecimal.ZERO, "VERY_POOR"));
    private static final int MIN_ORG_AVERAGE = 3;

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;
    private final EvalScoreCalculator scoreCalculator;

    public CompanyInsightService(JdbcTemplate jdbc, PermissionReader permissionReader,
                                 EvalScoreCalculator scoreCalculator) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
        this.scoreCalculator = scoreCalculator;
    }

    /** 홈에 넣을지 — DASHBOARD_COMPANY 는 부르는 쪽이 본다. */
    public static boolean canSeePerformance(Map<PermissionCode, PermissionScope> granted) {
        return granted.get(PermissionCode.EVAL_READ) == PermissionScope.ALL;
    }

    public static boolean canSeeLaborCost(Map<PermissionCode, PermissionScope> granted) {
        return granted.containsKey(PermissionCode.PAYROLL_READ);
    }

    // ------------------------------------------------------------------ 성과 현황

    /** 홈 — 기본 기간이 없으면 null(키 없음). */
    @Transactional(readOnly = true)
    public CompanyPerformance homePerformance(long companyId) {
        return defaultCycle(companyId).map(id -> performance(companyId, id)).orElse(null);
    }

    /** 기간 API — evalCycleId 가 없으면 기본 기간, 기본 기간도 없거나 없는 · 다른 회사 기간이면 404. */
    @Transactional(readOnly = true)
    public CompanyPerformance performance(LoginUser user, Long evalCycleId) {
        Map<PermissionCode, PermissionScope> granted = permissionReader.permissionsOf(user);
        if (!granted.containsKey(PermissionCode.DASHBOARD_COMPANY) || !canSeePerformance(granted)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        long cid = user.companyId();
        long cycleId = evalCycleId != null ? evalCycleId
                : defaultCycle(cid).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return performance(cid, cycleId).withCycles(availableCycles(cid));
    }

    private static final String HAS_CONFIRMED = """
            EXISTS (SELECT 1 FROM evaluation ev
                    JOIN employee e ON e.id = ev.target_employee_id AND e.company_id = ev.company_id
                    WHERE ev.company_id = c.company_id AND ev.eval_cycle_id = c.id AND ev.status = 'CONFIRMED'
                      AND e.status <> 'RESIGNED')
            """;

    /** 확정 결과가 있는 기간 중 가장 최근에 마감된 기간, 마감된 기간이 없으면 진행 중인 기간 중 가장 최근 기간. */
    private Optional<Long> defaultCycle(long cid) {
        return jdbc.queryForList("SELECT c.id FROM eval_cycle c WHERE c.company_id = ? AND " + HAS_CONFIRMED + """
                         ORDER BY (c.status = 'CLOSED') DESC, c.closed_at DESC NULLS LAST, c.start_date DESC, c.id DESC
                         LIMIT 1""",
                Long.class, cid).stream().findFirst();
    }

    private List<CompanyPerformance.CycleOption> availableCycles(long cid) {
        return jdbc.query("SELECT c.id, c.name, c.kind::text AS kind, c.closed_at FROM eval_cycle c WHERE c.company_id = ? AND "
                        + HAS_CONFIRMED + " ORDER BY c.start_date DESC, c.id DESC",
                (rs, i) -> new CompanyPerformance.CycleOption(rs.getLong("id"), rs.getString("name"), rs.getString("kind"),
                        rs.getObject("closed_at", OffsetDateTime.class)),
                cid);
    }

    private record Scored(long evaluationId, long employeeId, String employeeNo, String name, long orgUnitId,
                          String orgUnitName, String jobGradeName, BigDecimal totalScore) {
    }

    private CompanyPerformance performance(long cid, long cycleId) {
        Map<String, Object> cycle = jdbc.queryForList(
                        "SELECT name, kind::text AS kind FROM eval_cycle WHERE id = ? AND company_id = ?", cycleId, cid)
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<Scored> rows = jdbc.query("""
                        SELECT ev.id, e.id AS employee_id, e.employee_no, e.name, e.org_unit_id, o.name AS org_unit_name,
                               g.name AS job_grade_name
                        FROM evaluation ev
                             JOIN employee e ON e.id = ev.target_employee_id AND e.company_id = ev.company_id
                             JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                             LEFT JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                        WHERE ev.company_id = ? AND ev.eval_cycle_id = ? AND ev.status = 'CONFIRMED'
                          AND e.status <> 'RESIGNED'
                        """,
                (rs, i) -> new Scored(rs.getLong("id"), rs.getLong("employee_id"), rs.getString("employee_no"),
                        rs.getString("name"), rs.getLong("org_unit_id"), rs.getString("org_unit_name"),
                        rs.getString("job_grade_name"), null),
                cid, cycleId);
        var scores = scoreCalculator.scores(cid, rows.stream().map(Scored::evaluationId).toList());
        // 답이 빠져 총점이 없는 평가는 셀 수 없어 뺀다(확정됐다면 모두 답했다)
        List<Scored> ranked = rows.stream()
                .map(r -> new Scored(r.evaluationId(), r.employeeId(), r.employeeNo(), r.name(), r.orgUnitId(),
                        r.orgUnitName(), r.jobGradeName(), EvalScoreCalculator.total(scores.get(r.evaluationId()))))
                .filter(r -> r.totalScore() != null)
                .sorted(Comparator.comparing(Scored::totalScore).reversed().thenComparing(Scored::employeeNo))
                .toList();

        Map<String, Integer> levelCounts = new LinkedHashMap<>();
        LEVELS.forEach(l -> levelCounts.put(l.getValue(), 0));
        List<CompanyPerformance.Ranked> all = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Scored s = ranked.get(i);
            String level = levelOf(s.totalScore());
            levelCounts.merge(level, 1, Integer::sum);
            all.add(new CompanyPerformance.Ranked(i + 1, s.evaluationId(), s.employeeId(), s.employeeNo(), s.name(),
                    s.orgUnitName(), s.jobGradeName(), s.totalScore(), level));
        }
        List<CompanyPerformance.Ranked> top = all.subList(0, Math.min(TOP, all.size()));
        List<CompanyPerformance.Ranked> bottom = new ArrayList<>();
        for (int i = all.size() - 1; i >= TOP && bottom.size() < TOP; i--) {
            bottom.add(all.get(i));
        }

        OrgGroups groups = orgGroups(cid);
        Map<Long, List<BigDecimal>> byGroup = new HashMap<>();
        ranked.forEach(s -> byGroup.computeIfAbsent(groups.groupOf(s.orgUnitId()), k -> new ArrayList<>())
                .add(s.totalScore()));
        List<CompanyPerformance.OrgAverage> byOrg = groups.ordered(byGroup.keySet()).stream()
                .map(g -> {
                    List<BigDecimal> list = byGroup.get(g);
                    return new CompanyPerformance.OrgAverage(g, groups.name(g), list.size(),
                            list.size() < MIN_ORG_AVERAGE ? null : average(list));
                })
                .toList();

        return new CompanyPerformance(cycleId, (String) cycle.get("name"), (String) cycle.get("kind"), ranked.size(),
                ranked.isEmpty() ? null : average(ranked.stream().map(Scored::totalScore).toList()), levelCounts,
                top, bottom, byOrg, null);
    }

    private static String levelOf(BigDecimal totalScore) {
        BigDecimal avg = totalScore.divide(TWENTY, 1, RoundingMode.HALF_UP);
        return LEVELS.stream().filter(l -> avg.compareTo(l.getKey()) >= 0).findFirst().orElseThrow().getValue();
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ 직원별 인건비

    /** 홈 — 가장 최근 정산 월, 정산이 없으면 null(키 없음). */
    @Transactional(readOnly = true)
    public CompanyLaborCost homeLaborCost(long companyId) {
        List<String> months = settledMonths(companyId);
        if (months.isEmpty()) {
            return null;
        }
        LaborData data = laborData(companyId, months, months.get(0));
        return new CompanyLaborCost(data.payMonth, data.rows.size(), data.total, data.changeRate,
                data.rows.subList(0, Math.min(HOME_LABOR_TOP, data.rows.size())), data.byOrg, null, null);
    }

    /** 기간 API — payMonth 가 없으면 가장 최근 정산 월. 정산이 없는 달(취소 포함)이면 PAYROLL_NOT_FOUND. */
    @Transactional(readOnly = true)
    public CompanyLaborCost laborCost(LoginUser user, YearMonth payMonth, int page, int size) {
        Map<PermissionCode, PermissionScope> granted = permissionReader.permissionsOf(user);
        if (!granted.containsKey(PermissionCode.DASHBOARD_COMPANY) || !canSeeLaborCost(granted)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        long cid = user.companyId();
        List<String> months = settledMonths(cid);
        String month = payMonth != null ? payMonth.toString() : months.stream().findFirst().orElse(null);
        if (month == null || !months.contains(month)) {
            throw new BusinessException(ErrorCode.PAYROLL_NOT_FOUND);
        }
        LaborData data = laborData(cid, months, month);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        int from = (int) Math.min((long) pageNo * pageSize, data.rows.size());
        List<CompanyLaborCost.Row> content = data.rows.subList(from, Math.min(from + pageSize, data.rows.size()));
        PageResponse<CompanyLaborCost.Row> employees = new PageResponse<>(content, pageNo, pageSize, data.rows.size(),
                (data.rows.size() + pageSize - 1) / pageSize);
        return new CompanyLaborCost(data.payMonth, data.rows.size(), data.total, data.changeRate, null, data.byOrg,
                employees, months);
    }

    /** 취소되지 않은 정산의 귀속 월, 최근 순. */
    private List<String> settledMonths(long cid) {
        return jdbc.queryForList(
                "SELECT pay_month FROM payroll_run WHERE company_id = ? AND status <> 'CANCELLED' ORDER BY pay_month DESC",
                String.class, cid);
    }

    private record LaborData(String payMonth, long total, BigDecimal changeRate, List<CompanyLaborCost.Row> rows,
                             List<CompanyLaborCost.OrgCost> byOrg) {
    }

    private record Stub(long employeeId, String employeeNo, String name, long orgUnitId, String orgUnitName,
                        String jobGradeName, long grossPay, long companyBurden) {
        long laborCost() {
            return grossPay + companyBurden;
        }
    }

    /** months 는 최근 순, month 는 그 안에 있어야 한다. */
    private LaborData laborData(long cid, List<String> months, String month) {
        List<Stub> stubs = jdbc.query("""
                        SELECT p.employee_id, p.employee_no_snap, p.employee_name_snap, p.org_unit_id_snap, p.org_name_snap,
                               g.name AS job_grade_name, p.gross_pay, p.company_burden_total
                        FROM paystub p
                             JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = p.company_id
                             JOIN employee e ON e.id = p.employee_id AND e.company_id = p.company_id
                             LEFT JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                        WHERE p.company_id = ? AND r.pay_month = ? AND r.status <> 'CANCELLED'
                        ORDER BY p.gross_pay + p.company_burden_total DESC, p.employee_no_snap
                        """,
                (rs, i) -> new Stub(rs.getLong("employee_id"), rs.getString("employee_no_snap"),
                        rs.getString("employee_name_snap"), rs.getLong("org_unit_id_snap"), rs.getString("org_name_snap"),
                        rs.getString("job_grade_name"), rs.getLong("gross_pay"), rs.getLong("company_burden_total")),
                cid, month);
        long total = stubs.stream().mapToLong(Stub::laborCost).sum();
        List<CompanyLaborCost.Row> rows = stubs.stream()
                .map(s -> new CompanyLaborCost.Row(s.employeeId(), s.employeeNo(), s.name(), s.orgUnitName(),
                        s.jobGradeName(), s.grossPay(), s.companyBurden(), s.laborCost(), percent(s.laborCost(), total)))
                .toList();

        int at = months.indexOf(month);
        BigDecimal changeRate = null;
        if (at + 1 < months.size()) {
            long previous = monthTotal(cid, months.get(at + 1));
            changeRate = previous == 0 ? null : percent(total - previous, previous);
        }

        OrgGroups groups = orgGroups(cid);
        Map<Long, long[]> byGroup = new HashMap<>();   // [인원, 인건비]
        for (Stub s : stubs) {
            long[] acc = byGroup.computeIfAbsent(groups.groupOf(s.orgUnitId()), k -> new long[2]);
            acc[0]++;
            acc[1] += s.laborCost();
        }
        List<CompanyLaborCost.OrgCost> byOrg = groups.ordered(byGroup.keySet()).stream()
                .map(g -> new CompanyLaborCost.OrgCost(g, groups.name(g), (int) byGroup.get(g)[0], byGroup.get(g)[1]))
                .toList();
        return new LaborData(month, total, changeRate, rows, byOrg);
    }

    private long monthTotal(long cid, String month) {
        Long sum = jdbc.queryForObject("""
                        SELECT COALESCE(sum(p.gross_pay + p.company_burden_total), 0)
                        FROM paystub p JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = p.company_id
                        WHERE p.company_id = ? AND r.pay_month = ? AND r.status <> 'CANCELLED'
                        """,
                Long.class, cid, month);
        return sum == null ? 0 : sum;
    }

    /** part ÷ whole × 100, 소수 첫째 자리. whole 이 0 이면 null. */
    private static BigDecimal percent(long part, long whole) {
        return whole == 0 ? null : BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ 조직 묶기

    /**
     * 지금 조직 트리(사용 중지된 조직 포함)로 각 조직을 최상위 바로 아래 조직에 묶는다. 최상위 자신은 최상위로 묶여 맨 뒤에 온다.
     * 정산 당시 소속이 지금은 없는 조직이면(트리에 없음) 최상위로 본다.
     */
    private record OrgGroups(long rootId, Map<Long, Long> group, Map<Long, String> names, Map<Long, Integer> order) {

        long groupOf(long orgUnitId) {
            return group.getOrDefault(orgUnitId, rootId);
        }

        String name(long groupId) {
            return names.get(groupId);
        }

        /** 조직 순서(sort_order, id)대로, 최상위는 맨 뒤. */
        List<Long> ordered(java.util.Collection<Long> groupIds) {
            return groupIds.stream()
                    .sorted(Comparator.comparing((Function<Long, Boolean>) g -> g == rootId)
                            .thenComparing(order::get))
                    .toList();
        }
    }

    private OrgGroups orgGroups(long cid) {
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        Map<Long, Integer> order = new HashMap<>();
        long[] root = {-1};
        jdbc.query("SELECT id, parent_id, name FROM org_unit WHERE company_id = ? ORDER BY sort_order, id",
                rs -> {
                    long id = rs.getLong("id");
                    Long p = rs.getObject("parent_id", Long.class);
                    names.put(id, rs.getString("name"));
                    order.put(id, order.size());
                    if (p == null) {
                        root[0] = id;
                    } else {
                        parent.put(id, p);
                    }
                },
                cid);
        Map<Long, Long> group = new HashMap<>();
        for (long id : names.keySet()) {
            long g = id;
            while (parent.containsKey(g) && parent.get(g) != root[0]) {
                g = parent.get(g);
            }
            group.put(id, g);
        }
        return new OrgGroups(root[0], group, names, order);
    }
}
