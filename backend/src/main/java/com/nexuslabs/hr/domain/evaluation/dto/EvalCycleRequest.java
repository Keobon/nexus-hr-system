package com.nexuslabs.hr.domain.evaluation.dto;

import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/eval-cycles — 평가 기간 만들기(F-EVAL-01). kind 를 비우면 정기 평가. 승진 고과는 minYearsInGrade(1 이상)가 필수이고,
 * 정기 평가는 무시한다.
 */
public record EvalCycleRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        EvalCycleKind kind,
        Integer minYearsInGrade) {
}
