package com.nexuslabs.hr.domain.leave.dto;

import com.nexuslabs.hr.domain.leave.entity.HalfDay;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/me/leave-requests 본문(F-LEAVE-03). 지난 날짜도 신청할 수 있다(병가 사후 신청 등).
 * halfDay(AM · PM)는 반차 — 하루짜리(시작일 = 종료일), 잔여에서 차감하는 종류만(2026-11-15). 비우면 종일.
 */
public record LeaveRequestCreate(
        @NotNull Long leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 255) String reason,
        HalfDay halfDay) {
}
