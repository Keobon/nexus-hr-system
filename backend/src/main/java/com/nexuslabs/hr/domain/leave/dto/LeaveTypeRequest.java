package com.nexuslabs.hr.domain.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/leave-types 본문. 근속 가산 4개는 모두 있거나 모두 없어야 한다.
 * 등록 때 비운 선택 값: paid=true, prorateFirstYear=false, sortOrder=맨 뒤, isActive=true.
 * PATCH 는 API 설계서 1.1(보낸 필드만 바꾸고 null 은 비움)이라 LeaveTypeService.update 가 현재 값에 병합해 같은 규칙으로 검사한다.
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
        Boolean isActive) {
}
