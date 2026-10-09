package com.nexuslabs.hr.domain.evaluation.service;

import com.nexuslabs.hr.domain.evaluation.dto.EvalProgress;
import com.nexuslabs.hr.domain.evaluation.dto.EvalResultItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvalScore;
import com.nexuslabs.hr.domain.evaluation.dto.MyEvaluation;
import com.nexuslabs.hr.domain.evaluation.entity.EvaluationStatus;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 평가 결과 · 진행 현황(F-EVAL-05). 본인 · 조회 권한자는 **확정된** 결과만 본다(BR-EVAL-002). 점수는 저장하지 않고
 * 목록 전체를 쿼리 한 번으로 계산한다(EvalScoreCalculator).
 */
@Service
public class EvalResultService {

    private final JdbcTemplate jdbc;
    private final ScopeResolver scopeResolver;
    private final EvalScoreCalculator scoreCalculator;

    public EvalResultService(JdbcTemplate jdbc, ScopeResolver scopeResolver, EvalScoreCalculator scoreCalculator) {
        this.jdbc = jdbc;
        this.scopeResolver = scopeResolver;
        this.scoreCalculator = scoreCalculator;
    }

    /** 내 확정 결과 — 항목별 환산 점수 · 총점 · 종합의견(질문별 점수 없음). 최근 기간부터. */
    @Transactional(readOnly = true)
    public List<MyEvaluation> mine(LoginUser user) {
        List<Base> rows = jdbc.query(BASE_SELECT + """
                         WHERE ev.company_id = ? AND ev.target_employee_id = ? AND ev.status = 'CONFIRMED'
                         ORDER BY c.start_date DESC, c.id DESC
                        """,
                (rs, i) -> base(rs), user.companyId(), user.employeeId());
        Map<Long, List<EvalScore>> scores = scoreCalculator.scores(user.companyId(), rows.stream().map(Base::id).toList());
        return rows.stream().map(r -> {
            List<EvalScore> s = scores.getOrDefault(r.id(), List.of());
            return new MyEvaluation(r.id(), r.cycleId(), r.cycleName(), r.templateName(), s,
                    EvalScoreCalculator.total(s), r.overallComment(), r.confirmedAt());
        }).toList();
    }

    /** 범위 안 직원의 확정 결과. 팀 범위면 내 팀 직원만, orgUnitId 는 지금 소속 기준 하위 조직 포함. 최근 기간 → 사원번호 순. */
    @Transactional(readOnly = true)
    public PageImpl<EvalResultItem> results(LoginUser user, Long cycleId, Long orgUnitId, Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.EVAL_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder where = new StringBuilder(" WHERE ev.company_id = ? AND ev.status = 'CONFIRMED'");
        List<Object> args = new ArrayList<>(List.of(user.companyId()));
        if (!scope.all()) {
            where.append(" AND ev.target_employee_id = ANY(?)");
            args.add(scope.employeeIds().toArray(Long[]::new));
        }
        if (cycleId != null) {
            where.append(" AND ev.eval_cycle_id = ?");
            args.add(cycleId);
        }
        if (orgUnitId != null) {
            where.append("""
                     AND e.org_unit_id IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT c2.id FROM org_unit c2 JOIN sub s ON c2.parent_id = s.id WHERE c2.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(orgUnitId, user.companyId(), user.companyId()));
        }
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<Base> rows = jdbc.query(BASE_SELECT + where + " ORDER BY c.start_date DESC, c.id DESC, e.employee_no, ev.id"
                        + " LIMIT ? OFFSET ?",
                (rs, i) -> base(rs), pageArgs.toArray());
        Map<Long, List<EvalScore>> scores = scoreCalculator.scores(user.companyId(), rows.stream().map(Base::id).toList());
        List<EvalResultItem> items = rows.stream().map(r -> {
            List<EvalScore> s = scores.getOrDefault(r.id(), List.of());
            return new EvalResultItem(r.id(), r.cycleId(), r.cycleName(), r.targetId(), r.employeeNo(), r.targetName(),
                    r.orgUnitName(), r.templateName(), r.evaluatorName(), s, EvalScoreCalculator.total(s),
                    r.confirmedAt());
        }).toList();
        return new PageImpl<>(items, pageable, total);
    }

    /** 기간별 진행 현황(EVAL_MANAGE). 상태별 건수는 다섯 상태 모두(0 포함). 사원번호 순. */
    @Transactional(readOnly = true)
    public EvalProgress progress(LoginUser user, long cycleId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM eval_cycle WHERE id = ? AND company_id = ?)",
                Boolean.class, cycleId, user.companyId());
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<Base> rows = jdbc.query(BASE_SELECT + " WHERE ev.company_id = ? AND ev.eval_cycle_id = ? ORDER BY e.employee_no, ev.id",
                (rs, i) -> base(rs), user.companyId(), cycleId);
        Map<Long, List<EvalScore>> scores = scoreCalculator.scores(user.companyId(), rows.stream()
                .filter(r -> r.status() == EvaluationStatus.SUBMITTED || r.status() == EvaluationStatus.CONFIRMED)
                .map(Base::id).toList());
        Map<EvaluationStatus, Long> counts = new EnumMap<>(EvaluationStatus.class);
        for (EvaluationStatus s : EvaluationStatus.values()) {
            counts.put(s, 0L);
        }
        List<EvalProgress.Item> items = new ArrayList<>();
        for (Base r : rows) {
            counts.merge(r.status(), 1L, Long::sum);
            BigDecimal total = scores.containsKey(r.id()) ? EvalScoreCalculator.total(scores.get(r.id())) : null;
            items.add(new EvalProgress.Item(r.id(), r.targetId(), r.targetName(), r.orgUnitName(), r.evaluatorName(),
                    r.templateName(), r.status(), total));
        }
        return new EvalProgress(counts, items);
    }

    // ------------------------------------------------------------------

    private static Base base(ResultSet rs) throws SQLException {
        OffsetDateTime confirmedAt = rs.getObject("confirmed_at", OffsetDateTime.class);
        return new Base(rs.getLong("id"), rs.getLong("cycle_id"), rs.getString("cycle_name"), rs.getLong("target_id"),
                rs.getString("employee_no"), rs.getString("target_name"), rs.getString("org_unit_name"),
                rs.getString("evaluator_name"), rs.getString("template_name"),
                EvaluationStatus.valueOf(rs.getString("status")), rs.getString("overall_comment"),
                confirmedAt == null ? null : confirmedAt.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime());
    }

    private record Base(long id, long cycleId, String cycleName, long targetId, String employeeNo, String targetName,
                        String orgUnitName, String evaluatorName, String templateName, EvaluationStatus status,
                        String overallComment, OffsetDateTime confirmedAt) {
    }

    private static final String FROM = """
             FROM evaluation ev
                 JOIN eval_cycle c ON c.id = ev.eval_cycle_id AND c.company_id = ev.company_id
                 JOIN employee e ON e.id = ev.target_employee_id AND e.company_id = ev.company_id
                 JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                 JOIN employee ve ON ve.id = ev.evaluator_id AND ve.company_id = ev.company_id
                 JOIN eval_template t ON t.id = ev.eval_template_id AND t.company_id = ev.company_id
            """;

    private static final String BASE_SELECT = """
            SELECT ev.id, c.id AS cycle_id, c.name AS cycle_name, e.id AS target_id, e.employee_no, e.name AS target_name,
                   o.name AS org_unit_name, ve.name AS evaluator_name, t.name AS template_name, ev.status::text AS status,
                   ev.overall_comment, ev.confirmed_at
            """ + FROM;
}
