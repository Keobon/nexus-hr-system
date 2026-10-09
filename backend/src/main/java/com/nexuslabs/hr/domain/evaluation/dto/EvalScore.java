package com.nexuslabs.hr.domain.evaluation.dto;

import java.math.BigDecimal;

/**
 * 항목 하나의 점수(BR-EVAL-004) — 항목 점수 = 질문 점수 평균, 환산 점수 = (항목 점수 ÷ 5) × 배점. 소수 둘째 자리 반올림.
 * 그 항목 질문에 아직 답하지 않은 것이 있으면 둘 다 null.
 */
public record EvalScore(long criteriaId, String category, String name, int weight, BigDecimal itemScore,
                        BigDecimal convertedScore) {
}
