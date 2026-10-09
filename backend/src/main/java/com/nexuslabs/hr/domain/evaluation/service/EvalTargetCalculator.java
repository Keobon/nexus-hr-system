package com.nexuslabs.hr.domain.evaluation.service;

import com.nexuslabs.hr.domain.evaluation.dto.EvalTargetPreview;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 평가 대상 · 평가자 계산(F-EVAL-03, BR-EVAL-001 · 003). 저장하지 않는다 — 미리보기와 시작이 같은 계산을 쓴다.
 * 직원 · 조직 트리 · 규칙을 한 번씩 읽어 메모리에서 계산하므로 직원 수와 상관없이 쿼리 수가 일정하다.
 */
@Component
public class EvalTargetCalculator {

    private final JdbcTemplate jdbc;

    public EvalTargetCalculator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 재직 · 휴직 직원마다: 휴직이면 ON_LEAVE → 위에서부터 처음 맞는 규칙(없으면 NO_RULE, 템플릿 없는 규칙이면 RULE_EXCLUDED)
     * → 평가자(없으면 NO_EVALUATOR). 사원번호 순.
     */
    public EvalTargetPreview calculate(long companyId, long cycleId) {
        Map<Long, Org> orgs = new HashMap<>();
        jdbc.query("SELECT id, parent_id, lead_employee_id, is_active FROM org_unit WHERE company_id = ?",
                rs -> {
                    orgs.put(rs.getLong("id"), new Org(rs.getObject("parent_id", Long.class),
                            rs.getObject("lead_employee_id", Long.class), rs.getBoolean("is_active")));
                },
                companyId);
        Set<Long> leads = new HashSet<>();
        orgs.values().stream().filter(o -> o.active() && o.lead() != null).forEach(o -> leads.add(o.lead()));

        // 평가자 후보 = 재직중 + 계정 활성(승인자 후보와 같다, BR-APPR-002)
        Map<Long, String> candidates = new HashMap<>();
        jdbc.query("""
                        SELECT e.id, e.name FROM employee e
                        JOIN account a ON a.employee_id = e.id AND a.company_id = e.company_id
                        WHERE e.company_id = ? AND e.status = 'ACTIVE' AND a.is_active
                        """,
                rs -> {
                    candidates.put(rs.getLong("id"), rs.getString("name"));
                },
                companyId);

        List<Rule> rules = jdbc.query("""
                        SELECT r.priority, r.cond_is_org_lead, r.cond_job_title_id, r.cond_job_grade_id,
                               r.cond_employment_type_id, r.eval_template_id, t.name AS template_name
                        FROM eval_cycle_target r
                        LEFT JOIN eval_template t ON t.id = r.eval_template_id AND t.company_id = r.company_id
                        WHERE r.company_id = ? AND r.eval_cycle_id = ?
                        ORDER BY r.priority
                        """,
                (rs, i) -> new Rule(rs.getInt("priority"), rs.getObject("cond_is_org_lead", Boolean.class),
                        rs.getObject("cond_job_title_id", Long.class), rs.getObject("cond_job_grade_id", Long.class),
                        rs.getObject("cond_employment_type_id", Long.class), rs.getObject("eval_template_id", Long.class),
                        rs.getString("template_name")),
                companyId, cycleId);

        List<EvalTargetPreview.Target> targets = new ArrayList<>();
        List<EvalTargetPreview.Excluded> excluded = new ArrayList<>();
        jdbc.query("""
                        SELECT e.id, e.employee_no, e.name, e.status::text AS status, e.org_unit_id, o.name AS org_unit_name,
                               e.job_title_id, e.job_grade_id, e.employment_type_id
                        FROM employee e JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                        WHERE e.company_id = ? AND e.status IN ('ACTIVE', 'ON_LEAVE')
                        ORDER BY e.employee_no, e.id
                        """,
                rs -> {
                    long id = rs.getLong("id");
                    String no = rs.getString("employee_no");
                    String name = rs.getString("name");
                    String orgName = rs.getString("org_unit_name");
                    if ("ON_LEAVE".equals(rs.getString("status"))) {
                        excluded.add(new EvalTargetPreview.Excluded(id, no, name, orgName, EvalTargetPreview.Reason.ON_LEAVE));
                        return;
                    }
                    Long titleId = rs.getObject("job_title_id", Long.class);
                    Long gradeId = rs.getObject("job_grade_id", Long.class);
                    long typeId = rs.getLong("employment_type_id");
                    Rule rule = rules.stream().filter(r -> r.matches(leads.contains(id), titleId, gradeId, typeId))
                            .findFirst().orElse(null);
                    if (rule == null || rule.templateId() == null) {
                        excluded.add(new EvalTargetPreview.Excluded(id, no, name, orgName, rule == null
                                ? EvalTargetPreview.Reason.NO_RULE : EvalTargetPreview.Reason.RULE_EXCLUDED));
                        return;
                    }
                    Long evaluator = evaluator(orgs, candidates.keySet(), rs.getLong("org_unit_id"), id);
                    if (evaluator == null) {
                        excluded.add(new EvalTargetPreview.Excluded(id, no, name, orgName,
                                EvalTargetPreview.Reason.NO_EVALUATOR));
                        return;
                    }
                    targets.add(new EvalTargetPreview.Target(id, no, name, orgName, rule.priority(), rule.templateId(),
                            rule.templateName(), evaluator, candidates.get(evaluator)));
                },
                companyId);
        return new EvalTargetPreview(targets, excluded);
    }

    /**
     * 소속 조직의 조직장. 대상자 본인이거나 비었거나 후보가 아니면 상위 조직으로 올라간다(BR-EVAL-001).
     * 최상위까지 못 찾으면 null — 최상위 조직장은 평가자가 없어 대상이 아니다.
     */
    private static Long evaluator(Map<Long, Org> orgs, Set<Long> candidates, long orgUnitId, long targetId) {
        Set<Long> visited = new HashSet<>();
        Long org = orgUnitId;
        while (org != null && visited.add(org)) {
            Org o = orgs.get(org);
            if (o == null) {
                return null;
            }
            if (o.lead() != null && o.lead() != targetId && candidates.contains(o.lead())) {
                return o.lead();
            }
            org = o.parent();
        }
        return null;
    }

    private record Org(Long parent, Long lead, boolean active) {
    }

    private record Rule(int priority, Boolean condIsOrgLead, Long condJobTitleId, Long condJobGradeId,
                        Long condEmploymentTypeId, Long templateId, String templateName) {

        /** 조건이 비어 있으면 그 조건은 통과. 조직장 여부는 활성 조직의 조직장인지로 본다. */
        boolean matches(boolean isOrgLead, Long jobTitleId, Long jobGradeId, long employmentTypeId) {
            return (condIsOrgLead == null || condIsOrgLead == isOrgLead)
                    && (condJobTitleId == null || condJobTitleId.equals(jobTitleId))
                    && (condJobGradeId == null || condJobGradeId.equals(jobGradeId))
                    && (condEmploymentTypeId == null || condEmploymentTypeId == employmentTypeId);
        }
    }
}
