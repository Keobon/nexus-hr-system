package com.nexuslabs.hr.domain.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/leave-types · PATCH /api/leave-types/{id} — 수정도 전체 값을 보낸다.
 * 근속 가산 4개는 모두 있거나 모두 없어야 한다. null 인 선택 값: paid=true, prorateFirstYear=false,
 * sortOrder=맨 뒤(등록) / 그대로(수정), active=true(등록) / 그대로(수정).
 */
public record LeaveTypeRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull @Min(0) @Max(365) Integer annualDays,
        @NotNull Boolean deductsBalance,
        Boolean paid,
        Boolean prorateFirstYear,
        @Min(1) @Max(50) Integer seniorityStartYears,
        @Min(1) @Max(50) Integer seniorityIntervalYears,
        @Min(1) @Max(365) Integer seniorityAddDays,
        @Min(0) @Max(365) Integer seniorityMaxDays,
        Integer sortOrder,
        Boolean active) {
}
