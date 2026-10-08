package com.nexuslabs.hr.domain.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** POST /api/me/attendance/check-out — 퇴근(F-ATT-02). lat · lng 는 둘 다 있거나 둘 다 없다. */
public record CheckOutRequest(
        @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal lng) {
}
