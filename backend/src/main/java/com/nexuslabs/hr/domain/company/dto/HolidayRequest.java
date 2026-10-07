package com.nexuslabs.hr.domain.company.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.company.entity.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** POST /api/holidays — 휴일 등록(F-COMP-05). isRecurring 을 비우면 그해만 적용한다. */
public record HolidayRequest(
        @NotNull LocalDate holidayDate,
        @NotBlank @Size(max = 50) String name,
        @NotNull HolidayType holidayType,
        @JsonProperty("isRecurring") Boolean recurring) {
}
