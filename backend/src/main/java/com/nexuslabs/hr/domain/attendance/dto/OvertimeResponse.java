package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 연장근무 신청 상세(GET /api/overtime-requests/{id}, 신청 · 철회 응답). approvalSteps 는 API 설계서 9장 형식.
 * approvedMinutes 는 최종 승인 전에는 null.
 */
public record OvertimeResponse(long id, long employeeId, String employeeName, String orgUnitName, LocalDate workDate,
                               OffsetDateTime plannedStart, OffsetDateTime plannedEnd, int requestedMinutes,
                               Integer approvedMinutes, String reason, RequestStatus status, String cancelReason,
                               OffsetDateTime createdAt, List<ApprovalStepView> approvalSteps) {
}
