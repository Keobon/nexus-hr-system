package com.nexuslabs.hr.domain.attendance.dto;

/** GET /api/attendances 한 줄 — 직원 1명의 월 합계. */
public record EmployeeMonthlySummary(long employeeId, String employeeNo, String name, String orgUnitName,
                                     AttendanceSummary summary) {
}
