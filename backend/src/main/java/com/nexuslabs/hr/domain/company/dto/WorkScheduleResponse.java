package com.nexuslabs.hr.domain.company.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexuslabs.hr.domain.company.entity.WorkSchedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 근무시간 한 건. dailyStandardMinutes(1일 소정근로시간)는 저장하지 않고 퇴근 − 출근 − 휴게로 계산해 넣는다. */
public record WorkScheduleResponse(long id, LocalDate effectiveFrom,
                                   @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                   @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                   int breakMinutes, int lateGraceMinutes,
                                   @JsonFormat(pattern = "HH:mm") LocalTime nightStart,
                                   @JsonFormat(pattern = "HH:mm") LocalTime nightEnd,
                                   boolean overtimeApprovalRequired, List<Weekday> workDays,
                                   int dailyStandardMinutes) {

    public static WorkScheduleResponse from(WorkSchedule s) {
        int standard = (int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() - s.getBreakMinutes();
        return new WorkScheduleResponse(s.getId(), s.getEffectiveFrom(), s.getStartTime(), s.getEndTime(),
                s.getBreakMinutes(), s.getLateGraceMinutes(), s.getNightStart(), s.getNightEnd(),
                s.isOvertimeApprovalRequired(), Weekday.fromBits(s.getWorkDays()), standard);
    }
}
