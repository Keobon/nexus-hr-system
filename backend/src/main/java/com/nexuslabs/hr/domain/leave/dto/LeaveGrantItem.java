package com.nexuslabs.hr.domain.leave.dto;

import com.nexuslabs.hr.domain.leave.service.LeaveGrantType;

import java.time.OffsetDateTime;

/** 부여 내역 한 줄(F-LEAVE-02 조회). createdBy 는 자동 부여(정기·입사)면 null. */
public record LeaveGrantItem(long id, long employeeId, long leaveTypeId, String leaveTypeName, int leaveYear,
                             LeaveGrantType grantType, int days, String reason, Long createdById,
                             String createdByName, OffsetDateTime createdAt) {
}
