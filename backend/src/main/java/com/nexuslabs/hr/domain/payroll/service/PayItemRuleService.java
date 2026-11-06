package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.dto.PayItemRuleRequest;
import com.nexuslabs.hr.domain.payroll.dto.PayItemRuleSaved;
import com.nexuslabs.hr.domain.payroll.dto.PayItemRuleView;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import com.nexuslabs.hr.domain.payroll.entity.PayItemRule;
import com.nexuslabs.hr.domain.payroll.entity.PayRuleTarget;
import com.nexuslabs.hr.domain.payroll.repository.PayItemRepository;
import com.nexuslabs.hr.domain.payroll.repository.PayItemRuleRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 고정액 항목의 부서 · 직급 규칙(2026-11-06). 값은 적용 시작일과 함께 쌓고(해제도 행으로), 정산은 귀속 월 말일에 유효한 규칙을 쓴다
 * — {@link PayItemAmountResolver}. 직원 단위 금액은 직원 급여(SalaryService.assignItem)에서 받는다. 감사 로그 PAY_ITEM_RULE.
 */
@Service
public class PayItemRuleService {

    private static final String AUDIT_TARGET = "PAY_ITEM_RULE";

    private final PayItemRuleRepository repository;
    private final PayItemRepository payItemRepository;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public PayItemRuleService(PayItemRuleRepository repository, PayItemRepository payItemRepository, JdbcTemplate jdbc,
                              AuditLogger auditLogger, Clock clock) {
        this.repository = repository;
        this.payItemRepository = payItemRepository;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** 규칙(대상)마다 현재 · 예정 · 이력. 부서 규칙 → 직급 규칙, 이름 순. 다른 회사 항목은 404. */
    @Transactional(readOnly = true)
    public List<PayItemRuleView> list(long companyId, long payItemId) {
        get(payItemId);
        record Stored(PayRuleTarget type, Long orgUnitId, String orgUnitName, boolean includeSub, Long gradeId,
                      String gradeName, boolean orAbove, Long amount, LocalDate effectiveFrom, OffsetDateTime createdAt) {
        }
        List<Stored> rows = jdbc.query("""
                        SELECT r.target_type::text AS target_type, r.org_unit_id, o.name AS org_unit_name,
                               r.include_sub_orgs, r.job_grade_id, g.name AS job_grade_name, r.grade_or_above, r.amount,
                               r.effective_from, r.created_at
                        FROM pay_item_rule r
                        LEFT JOIN org_unit o ON o.id = r.org_unit_id AND o.company_id = r.company_id
                        LEFT JOIN job_grade g ON g.id = r.job_grade_id AND g.company_id = r.company_id
                        WHERE r.company_id = ? AND r.pay_item_id = ?
                        ORDER BY r.target_type, o.name, g.sort_order, r.include_sub_orgs, r.grade_or_above,
                                 r.effective_from, r.created_at, r.id
                        """,
                (rs, i) -> new Stored(PayRuleTarget.valueOf(rs.getString("target_type")),
                        rs.getObject("org_unit_id", Long.class), rs.getString("org_unit_name"),
                        rs.getBoolean("include_sub_orgs"), rs.getObject("job_grade_id", Long.class),
                        rs.getString("job_grade_name"), rs.getBoolean("grade_or_above"),
                        rs.getObject("amount", Long.class), rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("created_at", OffsetDateTime.class)),
                companyId, payItemId);
        LocalDate today = LocalDate.now(clock);
        Map<String, List<Stored>> byKey = new LinkedHashMap<>();
        rows.forEach(r -> byKey.computeIfAbsent(r.type() + ":" + r.orgUnitId() + ":" + r.includeSub() + ":"
                + r.gradeId() + ":" + r.orAbove(), k -> new ArrayList<>()).add(r));
        List<PayItemRuleView> result = new ArrayList<>();
        byKey.values().forEach(list -> {
            List<PayItemRuleView.Row> upcoming = new ArrayList<>();
            List<PayItemRuleView.Row> history = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Stored s = list.get(i);
                boolean superseded = i + 1 < list.size() && list.get(i + 1).effectiveFrom().equals(s.effectiveFrom());
                PayItemRuleView.Row row = new PayItemRuleView.Row(s.amount(), s.effectiveFrom(), s.createdAt(), superseded);
                (s.effectiveFrom().isAfter(today) ? upcoming : history).add(row);
            }
            List<PayItemRuleView.Row> latestFirst = new ArrayList<>(history.reversed());
            Stored k = list.getFirst();
            result.add(new PayItemRuleView(k.type(), k.orgUnitId(), k.orgUnitName(), k.includeSub(), k.gradeId(),
                    k.gradeName(), k.orAbove(), latestFirst.isEmpty() ? null : latestFirst.getFirst(), upcoming,
                    latestFirst));
        });
        return result;
    }

    /** 새 값(또는 해제)을 적용 시작일과 함께 더한다. 같은 대상 · 같은 날짜면 새 행이 정정본. 201 응답은 그 항목의 규칙 목록과 정산된 월 경고. */
    @Transactional
    public PayItemRuleSaved create(LoginUser user, long payItemId, PayItemRuleRequest request) {
        long cid = user.companyId();
        PayItem item = get(payItemId);
        if (item.getCalcMethod() != PayCalcMethod.FIXED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "적용 대상은 고정액 항목만 정할 수 있습니다");
        }
        if (!item.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        boolean org = request.targetType() == PayRuleTarget.ORG_UNIT;
        Long orgUnitId = org ? request.orgUnitId() : null;
        Long gradeId = org ? null : request.jobGradeId();
        if (org && (orgUnitId == null || !exists("org_unit", cid, orgUnitId))) {
            errors.put("orgUnitId", orgUnitId == null ? "부서를 고르세요" : "찾을 수 없는 부서입니다");
        }
        if (!org && (gradeId == null || !exists("job_grade", cid, gradeId))) {
            errors.put("jobGradeId", gradeId == null ? "직급을 고르세요" : "찾을 수 없는 직급입니다");
        }
        if (request.amount() != null && request.amount() < 0) {
            errors.put("amount", "금액은 0 이상이어야 합니다");
        }
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        boolean includeSub = org && !Boolean.FALSE.equals(request.includeSubOrgs());
        boolean orAbove = !org && Boolean.TRUE.equals(request.gradeOrAbove());
        PayItemRule saved = repository.saveAndFlush(new PayItemRule(payItemId, request.targetType(), orgUnitId,
                includeSub, gradeId, orAbove, request.amount(), request.effectiveFrom(), user.employeeId()));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("payItemId", payItemId);
        after.put("targetType", request.targetType().name());
        after.put("orgUnitId", orgUnitId);
        after.put("includeSubOrgs", includeSub);
        after.put("jobGradeId", gradeId);
        after.put("gradeOrAbove", orAbove);
        after.put("amount", request.amount());
        after.put("effectiveFrom", request.effectiveFrom().toString());
        auditLogger.log(user, AuditAction.CREATE, AUDIT_TARGET, saved.getId(), null, after);
        return new PayItemRuleSaved(list(cid, payItemId),
                SalaryService.settledWarning(jdbc, cid, request.effectiveFrom()));
    }

    private PayItem get(long id) {
        return payItemRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** table 은 이 클래스가 넘기는 상수만(org_unit · job_grade). */
    private boolean exists(String table, long companyId, long id) {
        return Objects.equals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE company_id = ? AND id = ?)", Boolean.class, companyId, id));
    }
}
