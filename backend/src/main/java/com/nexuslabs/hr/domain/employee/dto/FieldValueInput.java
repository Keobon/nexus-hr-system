package com.nexuslabs.hr.domain.employee.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 직원 추가 항목 값 한 건. seq 를 비우면 보낸 순서대로 1부터 매긴다(여러 건 허용 항목). */
public record FieldValueInput(@NotNull Long fieldDefId, @Min(1) Integer seq, @NotBlank String value) {
}
