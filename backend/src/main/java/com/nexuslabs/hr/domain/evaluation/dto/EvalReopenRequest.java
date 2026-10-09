package com.nexuslabs.hr.domain.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/evaluations/{id}/reopen — 사유 필수(F-EVAL-06). */
public record EvalReopenRequest(@NotBlank @Size(max = 255) String reason) {
}
