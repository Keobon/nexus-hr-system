package com.nexuslabs.hr.domain.assignment.service;

import com.nexuslabs.hr.domain.assignment.dto.PromotionCandidate;
import com.nexuslabs.hr.domain.evaluation.service.EvalScoreCalculator;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 승진 대상자(10/15 시연 피드백) — 지금 직급에 minYears 년 이상 있은 재직 직원. 승진 확정은 기존 승진 발령(POST /assignments)으로 한다.
 * 직급 시작일 = 직급이 바뀐 마지막 발령의 발효일(정정된 원본은 빼고 정정본으로 본다), 없으면 입사일.
 * JDBC 라 모든 쿼리에 company_id 조건을 직접 넣는다.
 */
@Service
public class PromotionCandidateService {

    /** 평가 평균을 내는 기간 — 평가 기간 종료일이 오늘부터 3년 안. */
    private static final int RECENT_EVAL_YEARS = 3;

    private static final String GRADE_SINCE = """
            COALESCE((SELECT max(h.effective_date) FROM assignment_history h
                      WHERE h.company_id = e.company_id AND h.employee_id = e.id
                        AND h.from_job_grade_id IS DISTINCT FROM h.to_job_grade_id
                        AND NOT EXISTS (SELECT 1 FROM assignment_history c
                                        WHERE c.company_id = h.company_id AND c.correction_of_id = h.id)),
                     e.hire_date)""";

    private final JdbcTemplate jdbc;
    private final ScopeResolver scopeResolver;
    private final EvalScoreCalculator scoreCalculator;
    private final Clock clock;

    public PromotionCandidateService(JdbcTemplate jdbc, ScopeResolver scopeResolver, EvalScoreCalculator scoreCalculator,
                                     Clock clock) {
        this.jdbc = jdbc;
        this.scopeResolver = scopeResolver;
        this.scoreCalculator = scoreCalculator;
        this.clock = clock;
    }

    /** 전사 발령 조회 권한만(팀 범위면 FORBIDDEN). 직급 시작일이 이른 순 → 사원번호 순. */
    @Transactional(readOnly = true)
    public PageImpl<PromotionCandidate> candidates(LoginUser user, int minYears, Pageable pageable) {
        if (!scopeResolver.scopeOf(user, PermissionCode.ASSIGNMENT_READ).all()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (minYears < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "체류 연수는 1년 이상으로 입력하세요");
        }
        LocalDate today = LocalDate.now(clock);
        String from = """
                 FROM (SELECT e.*, %s AS grade_since FROM employee e
                       WHERE e.company_id = ? AND e.status = 'ACTIVE' AND e.job_grade_id IS NOT NULL) e
                      JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                      JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                      LEFT JOIN job_title t ON t.id = e.job_title_id AND t.company_id = e.company_id
                 WHERE e.grade_since <= ?
                """.formatted(GRADE_SINCE);
        Object[] args = {user.companyId(), Date.valueOf(today.minusYears(minYears))};
        long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class, args);

        List<Object> pageArgs = new ArrayList<>(List.of(args));
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<PromotionCandidate> rows = jdbc.query("""
                        SELECT e.id, e.employee_no, e.name, o.name AS org_unit_name, e.job_grade_id, g.name AS job_grade_name,
                               t.name AS job_title_name, e.grade_since
                        """ + from + " ORDER BY e.grade_since, e.employee_no, e.id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    LocalDate since = rs.getObject("grade_since", LocalDate.class);
                    return new PromotionCandidate(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                            rs.getString("org_unit_name"), rs.getLong("job_grade_id"), rs.getString("job_grade_name"),
                            rs.getString("job_title_name"), since, Period.between(since, today).getYears(), null, 0);
                },
                pageArgs.toArray());
        return new PageImpl<>(withScores(user.companyId(), rows, today), pageable, total);
    }

    /** 최근 3년 확정 평가의 총점 평균 · 건수. 총점이 없는(답하지 않은 질문이 있는) 평가는 세지 않는다. */
    private List<PromotionCandidate> withScores(long companyId, List<PromotionCandidate> rows, LocalDate today) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<Long, Long> targetOf = new HashMap<>();
        jdbc.query("""
                        SELECT ev.id, ev.target_employee_id FROM evaluation ev
                        JOIN eval_cycle c ON c.id = ev.eval_cycle_id AND c.company_id = ev.company_id
                        WHERE ev.company_id = ? AND ev.status = 'CONFIRMED' AND ev.target_employee_id = ANY(?)
                          AND c.end_date >= ?""",
                rs -> {
                    targetOf.put(rs.getLong("id"), rs.getLong("target_employee_id"));
                },
                companyId, rows.stream().map(PromotionCandidate::employeeId).toArray(Long[]::new),
                Date.valueOf(today.minusYears(RECENT_EVAL_YEARS)));
        Map<Long, List<BigDecimal>> totals = new HashMap<>();
        scoreCalculator.scores(companyId, targetOf.keySet()).forEach((evaluationId, scores) -> {
            BigDecimal total = EvalScoreCalculator.total(scores);
            if (total != null) {
                totals.computeIfAbsent(targetOf.get(evaluationId), k -> new ArrayList<>()).add(total);
            }
        });
        return rows.stream().map(r -> {
            List<BigDecimal> list = totals.getOrDefault(r.employeeId(), List.of());
            BigDecimal average = list.isEmpty() ? null : list.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
            return new PromotionCandidate(r.employeeId(), r.employeeNo(), r.name(), r.orgUnitName(), r.jobGradeId(),
                    r.jobGradeName(), r.jobTitleName(), r.gradeSince(), r.yearsInGrade(), average, list.size());
        }).toList();
    }
}
