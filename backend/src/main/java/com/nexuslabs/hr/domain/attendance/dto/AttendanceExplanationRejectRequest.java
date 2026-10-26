package com.nexuslabs.hr.domain.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/attendance-explanations/{id}/reject — 반려 사유는 필수. */
public record AttendanceExplanationRejectRequest(@NotBlank @Size(max = 255) String reason) {
}
