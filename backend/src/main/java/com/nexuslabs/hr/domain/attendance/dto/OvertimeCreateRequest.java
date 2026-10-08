package com.nexuslabs.hr.domain.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/me/overtime-requests 본문(F-ATT-06). 예정 시각은 시:분("18:00", API 설계서 1.1).
 * 예정 종료가 시작보다 이르거나 같으면 다음 날 시각으로 본다(역할 분담 v2 2.3 23번).
 */
public record OvertimeCreateRequest(
        @NotNull LocalDate workDate,
        @NotBlank @Pattern(regexp = HH_MM, message = "시:분(예: 18:00) 형식이어야 합니다") String plannedStart,
        @NotBlank @Pattern(regexp = HH_MM, message = "시:분(예: 18:00) 형식이어야 합니다") String plannedEnd,
        @NotBlank @Size(max = 255) String reason) {

    public static final String HH_MM = "^([01]\\d|2[0-3]):[0-5]\\d$";
}
