package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.TripType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** POST /api/me/business-trips 본문(F-ATT-07). 지난 날짜도 신청할 수 있다(역할 분담 v2 2.3 25번). */
public record BusinessTripCreateRequest(
        @NotNull TripType tripType,
        @NotBlank @Size(max = 100) String destination,
        @NotBlank @Size(max = 500) String purpose,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @PositiveOrZero Long estimatedCost) {
}
