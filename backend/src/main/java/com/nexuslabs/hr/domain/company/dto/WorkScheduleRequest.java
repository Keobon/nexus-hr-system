package com.nexuslabs.hr.domain.company.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * POST /api/work-schedules — 새 근무시간(F-COMP-04). 수정 API 는 없고 새 적용 시작일로 행을 추가한다.
 * 비우면 야간 시간대는 22:00–06:00, 연장근무 승인 필요는 "예"다.
 */
public record WorkScheduleRequest(
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Min(0) @Max(720) Integer breakMinutes,
        @NotNull @Min(0) @Max(720) Integer lateGraceMinutes,
        LocalTime nightStart,
        LocalTime nightEnd,
        Boolean overtimeApprovalRequired,
        @NotEmpty List<Weekday> workDays) {
}
