package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * POST /api/me/attendance-explanations — 근태 소명(F-ATT-10). 실제 시각 · 근무 형태는 선택이다.
 * 사유는 승인 때 정정 사유 "소명: {사유}" 가 되므로 250자까지.
 */
public record AttendanceExplanationRequest(
        @NotNull LocalDate workDate,
        @NotBlank @Size(max = 250) String reason,
        OffsetDateTime checkInAt,
        OffsetDateTime checkOutAt,
        WorkType workType) {
}
