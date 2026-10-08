package com.nexuslabs.hr.domain.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** POST /api/me/leave-requests 본문(F-LEAVE-03). 지난 날짜도 신청할 수 있다(병가 사후 신청 등). */
public record LeaveRequestCreate(
        @NotNull Long leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 255) String reason) {
}
