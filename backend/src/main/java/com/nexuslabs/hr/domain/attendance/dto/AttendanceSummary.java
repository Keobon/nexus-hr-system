package com.nexuslabs.hr.domain.attendance.dto;

/**
 * 한 달 근태 합계(F-ATT-03). 급여 정산의 근태 연동 항목이 이 값으로 계산된다(F-PAY-05).
 * 횟수 · 일수와 분 단위 시간 합계다.
 */
public record AttendanceSummary(int lateCount, int earlyLeaveCount, int absentDays, int remoteDays, int fieldDays,
                                int tripDays, int workMinutes, int overtimeMinutes, int nightMinutes,
                                int holidayMinutes, int holidayOvertimeMinutes) {
}
