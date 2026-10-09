package com.nexuslabs.hr.domain.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * PUT /api/evaluations/{id}/answers — 임시저장(F-EVAL-04). 점수는 질문 단위로만 보낸다. 보낸 답으로 통째로 바꾼다
 * (빠진 질문은 미응답). 종합의견은 비울 수 있다.
 */
public record EvalAnswersRequest(@NotNull @Valid List<Answer> answers, String overallComment) {

    public record Answer(@NotNull Long questionId, @NotNull @Min(1) @Max(5) Integer score) {
    }
}
