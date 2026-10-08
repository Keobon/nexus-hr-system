package com.nexuslabs.hr.domain.attendance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/expense-types 본문(API 설계서 7.3). 비운 선택 값: receiptRequired=true, sortOrder=맨 뒤, isActive=true.
 * PATCH 는 API 설계서 1.1(보낸 필드만 바꿈)이라 본문을 Map 으로 받는다 — ExpenseTypeService.update.
 */
public record ExpenseTypeRequest(
        @NotBlank @Size(max = 50) String name,
        Boolean receiptRequired,
        Integer sortOrder,
        @JsonProperty("isActive") Boolean active) {
}
