package com.nexuslabs.hr.domain.evaluation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/me/evaluations 한 줄 — 확정된 내 결과만(F-EVAL-05). 질문별 점수는 없다. */
public record MyEvaluation(long evaluationId, long cycleId, String cycleName, String templateName,
                           List<EvalScore> criteria, BigDecimal totalScore, String overallComment,
                           OffsetDateTime confirmedAt) {
}
