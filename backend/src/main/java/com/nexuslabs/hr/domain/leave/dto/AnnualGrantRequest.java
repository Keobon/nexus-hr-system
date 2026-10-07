package com.nexuslabs.hr.domain.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** POST /api/leave-grants/annual */
public record AnnualGrantRequest(@NotNull @Min(2000) @Max(2100) Integer leaveYear) {
}
