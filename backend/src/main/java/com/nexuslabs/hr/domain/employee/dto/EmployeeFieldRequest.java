package com.nexuslabs.hr.domain.employee.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.employee.entity.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/employee-fields 본문(API 설계서 6장, F-EMP-07). options 는 SELECT 일 때만 쓰고 다른 타입이면 무시한다.
 * 비운 선택 값: isRequired · isMultiple · isSelfEditable = false, sortOrder = 맨 뒤, isActive = true.
 * PATCH 는 보낸 필드만 바꾸므로 본문을 Map 으로 받아 이 모양으로 합친다 — EmployeeFieldService.update.
 */
public record EmployeeFieldRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull FieldType fieldType,
        List<@NotBlank @Size(max = 100) String> options,
        @JsonProperty("isRequired") Boolean required,
        @JsonProperty("isMultiple") Boolean multiple,
        @JsonProperty("isSelfEditable") Boolean selfEditable,
        Integer sortOrder,
        @JsonProperty("isActive") Boolean active) {
}
