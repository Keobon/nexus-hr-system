package com.nexuslabs.hr.domain.evaluation.service;

import com.nexuslabs.hr.domain.evaluation.dto.EvalScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 평가 점수 계산(BR-EVAL-004). 점수는 저장하지 않고 조회할 때 계산한다. 여러 평가를 쿼리 한 번으로 계산한다(목록 N+1 방지).
 * 항목 점수 = 질문 점수 평균, 환산 점수 = (항목 점수 ÷ 5) × 배점 — 둘 다 소수 첫째 자리 반올림.
 * 총점 = 화면에 보이는 환산 점수의 합(표의 합계와 맞게). 답하지 않은 질문이 있는 항목은 null, 그러면 총점도 null.
 */
@Component
public class EvalScoreCalculator {

    private static final BigDecimal FIVE = BigDecimal.valueOf(5);

    private final JdbcTemplate jdbc;

    public EvalScoreCalculator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 평가 ID → 항목별 점수(정렬 순서대로). 다른 회사 평가 ID 는 결과에 없다. */
    public Map<Long, List<EvalScore>> scores(long companyId, Collection<Long> evaluationIds) {
        Map<Long, List<EvalScore>> result = new LinkedHashMap<>();
        if (evaluationIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                        SELECT ev.id AS evaluation_id, c.id AS criteria_id, c.category, c.name, c.weight,
                               count(q.id) AS question_count, count(a.id) AS answered, COALESCE(sum(a.score), 0) AS score_sum
                        FROM evaluation ev
                        JOIN eval_criteria c ON c.eval_template_id = ev.eval_template_id AND c.company_id = ev.company_id
                        JOIN eval_question q ON q.eval_criteria_id = c.id AND q.company_id = c.company_id
                        LEFT JOIN eval_answer a ON a.evaluation_id = ev.id AND a.eval_question_id = q.id
                                               AND a.company_id = ev.company_id
                        WHERE ev.company_id = ? AND ev.id = ANY(?)
                        GROUP BY ev.id, c.id, c.category, c.name, c.weight, c.sort_order
                        ORDER BY ev.id, c.sort_order, c.id
                        """,
                rs -> {
                    int questions = rs.getInt("question_count");
                    int answered = rs.getInt("answered");
                    int weight = rs.getInt("weight");
                    BigDecimal item = null;
                    BigDecimal converted = null;
                    if (answered == questions) {
                        BigDecimal sum = BigDecimal.valueOf(rs.getLong("score_sum"));
                        BigDecimal count = BigDecimal.valueOf(questions);
                        item = sum.divide(count, 1, RoundingMode.HALF_UP);
                        converted = sum.multiply(BigDecimal.valueOf(weight)).divide(count.multiply(FIVE), 1, RoundingMode.HALF_UP);
                    }
                    result.computeIfAbsent(rs.getLong("evaluation_id"), k -> new ArrayList<>())
                            .add(new EvalScore(rs.getLong("criteria_id"), rs.getString("category"), rs.getString("name"),
                                    weight, item, converted));
                },
                companyId, evaluationIds.stream().distinct().toArray(Long[]::new));
        return result;
    }

    /** 모든 항목에 환산 점수가 있을 때만 합계, 아니면 null. */
    public static BigDecimal total(List<EvalScore> scores) {
        if (scores == null || scores.isEmpty() || scores.stream().anyMatch(s -> s.convertedScore() == null)) {
            return null;
        }
        return scores.stream().map(EvalScore::convertedScore).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
    }
}
