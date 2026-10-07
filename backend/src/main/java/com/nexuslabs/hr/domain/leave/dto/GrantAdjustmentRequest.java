package com.nexuslabs.hr.domain.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/leave-grants/adjustments — days 는 + 또는 −(0 불가). */
public record GrantAdjustmentRequest(
        @NotNull Long employeeId,
        @NotNull Long leaveTypeId,
        @NotNull @Min(2000) @Max(2100) Integer leaveYear,
        @NotNull @Min(-365) @Max(365) Integer days,
        @NotBlank @Size(max = 255) String reason) {
}
