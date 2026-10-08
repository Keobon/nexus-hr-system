package com.nexuslabs.hr.domain.attendance.dto;

import java.util.List;

/** 한 직원의 한 달 근태 — 날짜별 행(그 달의 모든 날)과 월 합계. month 는 "2026-10" 형식. */
public record MonthlyAttendance(String month, List<AttendanceDay> days, AttendanceSummary summary) {
}
