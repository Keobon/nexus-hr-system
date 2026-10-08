package com.nexuslabs.hr.domain.leave.dto;

import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.leave.entity.LeaveStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 휴가 신청 목록 행 — GET /api/leave-requests · /api/me/leave-requests (API 설계서 8장). */
public record LeaveRequestRow(long id, long employeeId, String employeeName, String orgUnitName, long leaveTypeId,
                              String leaveTypeName, int leaveYear, LocalDate startDate, LocalDate endDate, int days,
                              LeaveStatus status, String reason, OffsetDateTime createdAt, CurrentStep currentStep) {

    public LeaveRequestRow withCurrentStep(CurrentStep step) {
        return new LeaveRequestRow(id, employeeId, employeeName, orgUnitName, leaveTypeId, leaveTypeName, leaveYear,
                startDate, endDate, days, status, reason, createdAt, step);
    }
}
