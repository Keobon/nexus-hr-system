package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.attendance.service.AttendanceDayStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 근태 조회의 하루(API 설계서 7.1, F-ATT-03). 시간 값은 모두 분 단위이고 저장하지 않고 계산한 값이다(BR-WORK-002).
 * 모든 날짜가 같은 필드를 갖는다 — 값이 없으면 null 이다. 출퇴근 기록이 없는 날(휴가 · 미기록 · 결근 · 휴직 · 쉬는 날)은
 * 계산값이 모두 null 이고, 근무일이 아닌데 기록도 없는 날은 status 도 null 이다.
 */
public record AttendanceDay(LocalDate date, boolean isWorkday, String holidayName, AttendanceDayStatus status,
                            WorkType workType, OffsetDateTime checkInAt, OffsetDateTime checkOutAt, Boolean late,
                            Boolean earlyLeave, Integer workMinutes, Integer overtimeMinutes, Integer nightMinutes,
                            Integer holidayMinutes, Integer holidayOvertimeMinutes, Integer approvedOvertimeMinutes) {
}
