package com.nexuslabs.hr.domain.approval.service;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 승인자 계산(기능명세서 9.1, BR-APPR-001–004). 저장하지 않고 계산만 한다 — 저장은 ApprovalService.
 * 조직 트리·후보 직원을 한 번에 읽어 메모리에서 계산하므로 단계 수와 상관없이 쿼리 수가 일정하다.
 */
@Component
public class ApproverCalculator {

    private final JdbcTemplate jdbc;

    public ApproverCalculator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 승인선을 골라 단계별 승인자를 계산한다. 승인자를 못 찾으면 APPROVER_NOT_FOUND. */
    public ApprovalPlan plan(long companyId, ApprovalWorkType workType, long applicantId) {
        if (workType == ApprovalWorkType.LEAVE_CANCEL) {
            throw new IllegalArgumentException("휴가 취소는 승인선이 없다 — ApprovalService.openLeaveCancel 을 쓴다");
        }
        Applicant applicant = applicant(companyId, applicantId);
        Line line = selectLine(companyId, workType, applicant);
        Context ctx = context(companyId);

        List<ApprovalPlan.PlannedStep> steps = new ArrayList<>();
        Long previousApprover = null;
        for (LineStep s : line.steps()) {
            Long approver = resolve(ctx, s, applicant);
            ApprovalPlan.SkipReason skip = null;
            if (approver == null) {
                // 위로 올라가도 못 찾음 — 신청자가 최상위 조직장이면 생략, 아니면 신청 거부(BR-APPR-003)
                if (!ctx.isTopLead(applicant.id())) {
                    throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND,
                            Map.of("stepOrder", s.stepOrder(), "approverType", s.type().name()));
                }
                skip = ApprovalPlan.SkipReason.TOP_OF_ORG;
            } else if (approver == applicant.id()) {
                skip = ApprovalPlan.SkipReason.SELF;
            } else if (approver.equals(previousApprover)) {
                skip = ApprovalPlan.SkipReason.SAME_AS_PREVIOUS;
            }
            steps.add(new ApprovalPlan.PlannedStep(s.stepOrder(), approver,
                    approver == null ? null : ctx.names().get(approver), skip));
            previousApprover = approver;
        }
        return new ApprovalPlan(line.id(), line.name(), steps);
    }

    /** 승인자 후보인지(재직중 + 계정 활성, BR-APPR-002). 재지정할 때도 쓴다. */
    public boolean isCandidate(long companyId, long employeeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM employee e
                                       JOIN account a ON a.employee_id = e.id AND a.company_id = e.company_id
                                       WHERE e.id = ? AND e.company_id = ? AND e.status = 'ACTIVE' AND a.is_active)
                        """,
                Boolean.class, employeeId, companyId));
    }

    private Long resolve(Context ctx, LineStep step, Applicant applicant) {
        return switch (step.type()) {
            // 소속 조직장 — 비었거나 본인이거나 후보가 아니면 위로
            case ORG_LEAD -> firstLeadUpward(ctx, applicant.orgUnitId(), applicant.id());
            // N단계 위 조직의 조직장 — 비었으면 그 위로
            case ORG_LEAD_UP -> {
                Long org = applicant.orgUnitId();
                for (int i = 0; i < step.upLevels() && org != null; i++) {
                    org = ctx.parent().get(org);
                }
                yield org == null ? null : firstLeadUpward(ctx, org, null);
            }
            // 그 직책의 후보가 정확히 1명일 때만
            case JOB_TITLE -> {
                List<Long> holders = ctx.titleHolders().getOrDefault(step.jobTitleId(), List.of());
                yield holders.size() == 1 ? holders.getFirst() : null;
            }
            case EMPLOYEE -> ctx.candidates().contains(step.employeeId()) ? step.employeeId() : null;
        };
    }

    /** org 부터 위로 올라가며 후보인 조직장을 찾는다. skipEmployee 는 건너뛸 사람(소속 조직장 계산의 신청자 본인). */
    private static Long firstLeadUpward(Context ctx, Long org, Long skipEmployee) {
        Set<Long> visited = new HashSet<>();
        while (org != null && visited.add(org)) {
            Long lead = ctx.lead().get(org);
            if (lead != null && !lead.equals(skipEmployee) && ctx.candidates().contains(lead)) {
                return lead;
            }
            org = ctx.parent().get(org);
        }
        return null;
    }

    private Applicant applicant(long companyId, long applicantId) {
        return jdbc.query("""
                        SELECT e.id, e.org_unit_id, e.job_title_id, a.role_id
                        FROM employee e LEFT JOIN account a ON a.employee_id = e.id AND a.company_id = e.company_id
                        WHERE e.id = ? AND e.company_id = ?
                        """,
                (rs, i) -> new Applicant(rs.getLong("id"), rs.getLong("org_unit_id"),
                        rs.getObject("job_title_id", Long.class), rs.getObject("role_id", Long.class)),
                applicantId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 조건(직책 또는 역할)이 맞는 활성 승인선을 우선순위 순으로, 없으면 기본 승인선. */
    private Line selectLine(long companyId, ApprovalWorkType workType, Applicant applicant) {
        Long lineId = jdbc.query("""
                        SELECT id FROM approval_line
                        WHERE company_id = ? AND work_type = ?::approval_work_type AND is_active
                          AND (is_default
                               OR (cond_job_title_id IS NOT NULL AND cond_job_title_id = ?)
                               OR (cond_role_id IS NOT NULL AND cond_role_id = ?))
                        ORDER BY is_default, priority, id
                        LIMIT 1
                        """,
                (rs, i) -> rs.getLong("id"),
                companyId, workType.name(), applicant.jobTitleId(), applicant.roleId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("기본 승인선이 없다: " + workType)); // BR-APPR-001 위반
        String name = jdbc.queryForObject("SELECT name FROM approval_line WHERE id = ? AND company_id = ?",
                String.class, lineId, companyId);
        List<LineStep> steps = jdbc.query("""
                        SELECT step_order, approver_type::text AS approver_type, up_levels, job_title_id, employee_id
                        FROM approval_line_step WHERE approval_line_id = ? AND company_id = ?
                        ORDER BY step_order
                        """,
                (rs, i) -> new LineStep(rs.getInt("step_order"), ApproverType.valueOf(rs.getString("approver_type")),
                        rs.getInt("up_levels"), rs.getObject("job_title_id", Long.class),
                        rs.getObject("employee_id", Long.class)),
                lineId, companyId);
        return new Line(lineId, name, steps);
    }

    private Context context(long companyId) {
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Long> lead = new HashMap<>();
        Long[] root = new Long[1];
        jdbc.query("SELECT id, parent_id, lead_employee_id FROM org_unit WHERE company_id = ?", rs -> {
            long id = rs.getLong("id");
            Long p = rs.getObject("parent_id", Long.class);
            parent.put(id, p);
            lead.put(id, rs.getObject("lead_employee_id", Long.class));
            if (p == null) {
                root[0] = id;
            }
        }, companyId);

        Set<Long> candidates = new HashSet<>();
        Map<Long, List<Long>> titleHolders = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        jdbc.query("""
                SELECT e.id, e.name, e.job_title_id, (e.status = 'ACTIVE' AND COALESCE(a.is_active, FALSE)) AS candidate
                FROM employee e LEFT JOIN account a ON a.employee_id = e.id AND a.company_id = e.company_id
                WHERE e.company_id = ?
                """, rs -> {
            long id = rs.getLong("id");
            names.put(id, rs.getString("name"));
            if (rs.getBoolean("candidate")) {
                candidates.add(id);
                Long title = rs.getObject("job_title_id", Long.class);
                if (title != null) {
                    titleHolders.computeIfAbsent(title, k -> new ArrayList<>()).add(id);
                }
            }
        }, companyId);
        return new Context(parent, lead, root[0], candidates, titleHolders, names);
    }

    private record Applicant(long id, long orgUnitId, Long jobTitleId, Long roleId) {
    }

    private record Line(long id, String name, List<LineStep> steps) {
    }

    private record LineStep(int stepOrder, ApproverType type, int upLevels, Long jobTitleId, Long employeeId) {
    }

    private record Context(Map<Long, Long> parent, Map<Long, Long> lead, Long rootOrgUnitId, Set<Long> candidates,
                           Map<Long, List<Long>> titleHolders, Map<Long, String> names) {

        /** 최상위 조직의 조직장인지 — 위에 아무도 없어 단계를 생략할 수 있다. */
        boolean isTopLead(long employeeId) {
            return rootOrgUnitId != null && Objects.equals(lead.get(rootOrgUnitId), employeeId);
        }
    }
}
