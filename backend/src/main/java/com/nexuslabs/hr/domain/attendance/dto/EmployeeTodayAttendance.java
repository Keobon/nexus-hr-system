package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.attendance.service.TodayStatus;

import java.time.OffsetDateTime;

/**
 * 부서원 오늘 근태 한 줄(GET /api/attendances/today, 역할 분담 2.3 K 85). todayStatus 는 직원 목록과 같은 계산이다.
 * 자정을 넘겨 어제 근무가 이어지는 중이면 어제 행의 값이 온다. 값이 없는 필드는 null 로 내려간다.
 */
public record EmployeeTodayAttendance(long employeeId, String name, String orgUnitName, TodayStatus todayStatus,
                                      WorkType workType, String placeMemo, OffsetDateTime checkInAt,
                                      OffsetDateTime checkOutAt) {
}
