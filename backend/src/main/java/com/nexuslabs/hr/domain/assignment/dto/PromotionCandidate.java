package com.nexuslabs.hr.domain.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 승진 대상자 한 줄(GET /api/promotions/candidates). gradeSince 는 마지막 직급 변경 발령일(없으면 입사일), yearsInGrade 는 채운 햇수.
 * averageScore 는 최근 3년간 확정된 평가 총점의 평균(소수 첫째 자리) — 없으면 null, evaluationCount 는 0.
 */
public record PromotionCandidate(long employeeId, String employeeNo, String name, String orgUnitName, long jobGradeId,
                                 String jobGradeName, String jobTitleName, LocalDate gradeSince, int yearsInGrade,
                                 BigDecimal averageScore, int evaluationCount) {
}
