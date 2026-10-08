package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/me/attendance/check-in — 출근(F-ATT-01). workType 을 비우면 사내(OFFICE)다.
 * 위치는 브라우저가 허용했을 때만 오므로 lat · lng 는 둘 다 있거나 둘 다 없다.
 */
public record CheckInRequest(
        WorkType workType,
        @Size(max = 100) String placeMemo,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal lng) {
}
