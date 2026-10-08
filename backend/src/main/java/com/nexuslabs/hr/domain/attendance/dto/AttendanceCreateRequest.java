package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * POST /api/attendances — 기록이 없는 날에 근태를 만드는 정정(F-ATT-04). status 범위와 규칙은 정정(PATCH)과 같다.
 */
public record AttendanceCreateRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate workDate,
        @NotNull AttendanceStatus status,
        OffsetDateTime checkInAt,
        OffsetDateTime checkOutAt,
        WorkType workType,
        @NotBlank @Size(max = 255) String reason) {
}
