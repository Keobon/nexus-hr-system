package com.nexuslabs.hr.domain.employee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** PUT /api/employees/{id}/field-values · /api/me/field-values 본문 — 대상 항목의 값 전체 교체. 빈 목록이면 모두 지운다. */
public record FieldValuesRequest(@NotNull List<@Valid @NotNull FieldValueInput> values) {
}
