package com.nexuslabs.hr.domain.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST · PUT /api/eval-templates — 템플릿 + 항목 + 질문을 통째로 저장(F-EVAL-02). 질문 문구 필드는 DB와 같은 content.
 * 가중치 합 100(EVAL_WEIGHT_SUM_INVALID), 항목마다 질문 1개 이상(EVAL_NO_QUESTION)은 서비스에서 본다.
 */
public record EvalTemplateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotEmpty @Valid List<Criteria> criteria) {

    public record Criteria(
            @NotBlank @Size(max = 30) String category,
            @NotBlank @Size(max = 50) String name,
            @NotNull @Min(1) @Max(100) Integer weight,
            Integer sortOrder,
            @Valid List<Question> questions) {
    }

    public record Question(
            @NotBlank @Size(max = 500) String content,
            Integer sortOrder) {
    }
}
