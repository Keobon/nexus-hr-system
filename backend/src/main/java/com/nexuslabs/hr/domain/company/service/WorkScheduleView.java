package com.nexuslabs.hr.domain.company.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/** 어떤 날짜에 적용되는 근무시간 기준(WorkCalendar 가 돌려준다). 다른 영역이 엔티티 대신 이 값을 받는다. */
public record WorkScheduleView(LocalDate effectiveFrom, LocalTime startTime, LocalTime endTime, int breakMinutes,
                               int lateGraceMinutes, LocalTime nightStart, LocalTime nightEnd,
                               boolean overtimeApprovalRequired, Set<DayOfWeek> workDays) {

    /** 1일 소정근로시간(분) = 기준 퇴근 − 기준 출근 − 휴게(F-ATT-03). */
    public int dailyStandardMinutes() {
        return (int) Duration.between(startTime, endTime).toMinutes() - breakMinutes;
    }
}
