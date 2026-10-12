package com.nexuslabs.hr.domain.leave.dto;

import com.nexuslabs.hr.domain.leave.entity.LeaveStatus;

import java.time.LocalDate;

/** 팀 휴가 캘린더 한 줄(GET /me/team-leaves). 사유 · 결재 정보는 넣지 않는다. */
public record TeamLeaveRow(long employeeId, String employeeName, String leaveTypeName, LocalDate startDate,
                           LocalDate endDate, LeaveStatus status) {
}
