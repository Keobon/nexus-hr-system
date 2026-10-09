package com.nexuslabs.hr.domain.evaluation.dto;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/evaluations/{id}/evaluator — 새 평가자(F-EVAL-06, 2026-10-10 추가). */
public record EvalEvaluatorRequest(@NotNull Long evaluatorId) {
}
