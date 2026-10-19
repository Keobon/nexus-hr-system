package com.nexuslabs.hr.domain.evaluation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/evaluations/results 한 줄 — 범위 안 직원의 확정된 결과(F-EVAL-05). orgUnitName 은 지금 소속.
 * 종합의견은 본인에게도 공개되는 글이라 조회 권한자도 본다(2026-10-19).
 */
public record EvalResultItem(long evaluationId, long cycleId, String cycleName, long targetId, String employeeNo,
                             String targetName, String orgUnitName, String templateName, String evaluatorName,
                             List<EvalScore> criteria, BigDecimal totalScore, String overallComment,
                             OffsetDateTime confirmedAt) {
}
