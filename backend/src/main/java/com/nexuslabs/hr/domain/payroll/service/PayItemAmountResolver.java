package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.entity.PayApplyTo;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 고정액 항목의 직원별 금액(2026-11-06) — 그날 유효한 값 중 처음 맞는 하나: ① 직원 개별 금액(employee_pay_item)
 * ② 직급 규칙("그 직급만"이 먼저, 없으면 "이상" 중 기준이 가장 높은 것) ③ 부서 규칙(가까운 부서 먼저 — 자기 부서는 포함 여부와
 * 상관없이, 상위 부서는 "하위 포함"일 때만) ④ 항목 기본 금액(전원 항목만). 부서 · 직급은 직원의 지금 값이다(발령은 늘 오늘 발효).
 * 정산 미리보기 · 확정과 직원 급여 조회가 같은 계산을 쓴다. 쿼리 수는 직원 수와 상관없이 일정하다.
 */
@Component
public class PayItemAmountResolver {

    public enum SourceType { EMPLOYEE, JOB_GRADE, ORG_UNIT, DEFAULT }

    /** name: 부서 이름 · "과장 이상" · "과장" · null(개별 · 기본). */
    public record Source(SourceType type, String name) {

        /** 명세서 산식 칸 — "개발본부 규칙" · "과장 이상 규칙" · "개별 금액", 기본 금액은 null. */
        public String note() {
            return switch (type) {
                case EMPLOYEE -> "개별 금액";
                case JOB_GRADE, ORG_UNIT -> name + " 규칙";
                case DEFAULT -> null;
            };
        }
    }

    public record Resolved(long amount, Source source) {
    }

    private record Emp(long orgUnitId, Long jobGradeId) {
    }

    private record Org(Long parentId, String name) {
    }

    private record Grade(int sortOrder, String name) {
    }

    private record Rule(long itemId, String type, Long orgUnitId, boolean includeSub, Long gradeId, boolean orAbove,
                        long amount) {
    }

    private final JdbcTemplate jdbc;

    public PayItemAmountResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 직원 → (고정액 항목 → 금액 · 출처). 전원 항목은 늘 들어 있고, 지정 직원 항목은 맞는 규칙 · 개별 금액이 있을 때만. */
    public Map<Long, Map<Long, Resolved>> resolve(long companyId, Collection<Long> employeeIds, List<PayItem> items,
                                                  LocalDate date) {
        Map<Long, Map<Long, Resolved>> result = new HashMap<>();
        List<PayItem> fixed = items.stream().filter(i -> i.getCalcMethod() == PayCalcMethod.FIXED).toList();
        if (employeeIds.isEmpty() || fixed.isEmpty()) {
            return result;
        }
        Long[] ids = employeeIds.stream().distinct().toArray(Long[]::new);
        Map<Long, Emp> emps = new HashMap<>();
        jdbc.query("SELECT id, org_unit_id, job_grade_id FROM employee WHERE company_id = ? AND id = ANY(?)",
                rs -> {
                    emps.put(rs.getLong("id"), new Emp(rs.getLong("org_unit_id"), rs.getObject("job_grade_id", Long.class)));
                },
                companyId, ids);
        Map<Long, Org> orgs = new HashMap<>();
        jdbc.query("SELECT id, parent_id, name FROM org_unit WHERE company_id = ?",
                rs -> {
                    orgs.put(rs.getLong("id"), new Org(rs.getObject("parent_id", Long.class), rs.getString("name")));
                },
                companyId);
        Map<Long, Grade> grades = new HashMap<>();
        jdbc.query("SELECT id, sort_order, name FROM job_grade WHERE company_id = ?",
                rs -> {
                    grades.put(rs.getLong("id"), new Grade(rs.getInt("sort_order"), rs.getString("name")));
                },
                companyId);
        // 대상마다 그날 유효한 행 — 해제(null)된 대상은 뺀다
        List<Rule> rules = new ArrayList<>();
        jdbc.query("""
                        SELECT * FROM (
                            SELECT DISTINCT ON (pay_item_id, target_type, org_unit_id, include_sub_orgs, job_grade_id,
                                                grade_or_above)
                                   pay_item_id, target_type::text AS target_type, org_unit_id, include_sub_orgs,
                                   job_grade_id, grade_or_above, amount
                            FROM pay_item_rule WHERE company_id = ? AND effective_from <= ?
                            ORDER BY pay_item_id, target_type, org_unit_id, include_sub_orgs, job_grade_id, grade_or_above,
                                     effective_from DESC, created_at DESC, id DESC
                        ) v WHERE amount IS NOT NULL
                        """,
                rs -> {
                    rules.add(new Rule(rs.getLong("pay_item_id"), rs.getString("target_type"),
                            rs.getObject("org_unit_id", Long.class), rs.getBoolean("include_sub_orgs"),
                            rs.getObject("job_grade_id", Long.class), rs.getBoolean("grade_or_above"),
                            rs.getLong("amount")));
                },
                companyId, Date.valueOf(date));
        Map<Long, Map<Long, Long>> personal = new HashMap<>();
        jdbc.query("""
                        SELECT * FROM (
                            SELECT DISTINCT ON (employee_id, pay_item_id) employee_id, pay_item_id, amount
                            FROM employee_pay_item
                            WHERE company_id = ? AND employee_id = ANY(?) AND effective_from <= ?
                            ORDER BY employee_id, pay_item_id, effective_from DESC, created_at DESC, id DESC
                        ) v WHERE amount IS NOT NULL
                        """,
                rs -> {
                    personal.computeIfAbsent(rs.getLong("employee_id"), k -> new HashMap<>())
                            .put(rs.getLong("pay_item_id"), rs.getLong("amount"));
                },
                companyId, ids, Date.valueOf(date));

        for (Long employeeId : ids) {
            Emp emp = emps.get(employeeId);
            if (emp == null) {
                continue;
            }
            Map<Long, Resolved> mine = new HashMap<>();
            for (PayItem item : fixed) {
                Resolved r = one(item, emp, rules, personal.getOrDefault(employeeId, Map.of()), orgs, grades);
                if (r != null) {
                    mine.put(item.getId(), r);
                }
            }
            result.put(employeeId, mine);
        }
        return result;
    }

    private static Resolved one(PayItem item, Emp emp, List<Rule> rules, Map<Long, Long> personal, Map<Long, Org> orgs,
                                Map<Long, Grade> grades) {
        Long own = personal.get(item.getId());
        if (own != null) {
            return new Resolved(own, new Source(SourceType.EMPLOYEE, null));
        }
        List<Rule> forItem = rules.stream().filter(r -> r.itemId() == item.getId()).toList();
        // 직급 — 그 직급만 > 이상(기준이 높을수록 먼저)
        Grade mine = emp.jobGradeId() == null ? null : grades.get(emp.jobGradeId());
        if (mine != null) {
            Rule exact = forItem.stream()
                    .filter(r -> "JOB_GRADE".equals(r.type()) && !r.orAbove() && emp.jobGradeId().equals(r.gradeId()))
                    .findFirst().orElse(null);
            if (exact != null) {
                return new Resolved(exact.amount(), new Source(SourceType.JOB_GRADE, mine.name()));
            }
            Rule above = null;
            int best = Integer.MIN_VALUE;
            for (Rule r : forItem) {
                Grade g = r.gradeId() == null ? null : grades.get(r.gradeId());
                if ("JOB_GRADE".equals(r.type()) && r.orAbove() && g != null && g.sortOrder() <= mine.sortOrder()
                        && g.sortOrder() > best) {
                    above = r;
                    best = g.sortOrder();
                }
            }
            if (above != null) {
                return new Resolved(above.amount(),
                        new Source(SourceType.JOB_GRADE, grades.get(above.gradeId()).name() + " 이상"));
            }
        }
        // 부서 — 자기 부서부터 위로
        Set<Long> visited = new HashSet<>();
        Long org = emp.orgUnitId();
        boolean self = true;
        while (org != null && visited.add(org)) {
            final long at = org;
            final boolean isSelf = self;
            Rule hit = forItem.stream()
                    .filter(r -> "ORG_UNIT".equals(r.type()) && r.orgUnitId() == at && (isSelf || r.includeSub()))
                    // 자기 부서에 "하위 미포함" · "하위 포함" 규칙이 둘 다 있으면 "미포함"(그 부서 전용)이 먼저
                    .min((a, b) -> Boolean.compare(a.includeSub(), b.includeSub()))
                    .orElse(null);
            if (hit != null) {
                return new Resolved(hit.amount(), new Source(SourceType.ORG_UNIT, orgs.get(at).name()));
            }
            Org o = orgs.get(at);
            org = o == null ? null : o.parentId();
            self = false;
        }
        if (item.getApplyTo() == PayApplyTo.ALL) {
            return new Resolved(item.getDefaultAmount() == null ? 0 : item.getDefaultAmount(),
                    new Source(SourceType.DEFAULT, null));
        }
        return null;
    }
}
