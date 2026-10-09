package com.nexuslabs.hr.domain.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** POST /api/eval-cycles — 평가 기간 만들기(F-EVAL-01). */
public record EvalCycleRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
