package com.nexuslabs.hr.domain.leave.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.leave.entity.LeaveStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 휴가 상세 — GET /api/leave-requests/{id} 와 신청·철회·취소 요청의 응답(API 설계서 8장).
 * approvalSteps 는 신청 단계 뒤에 취소 요청 단계를 이어 붙인다. 취소 단계의 round 는 신청의 마지막 round 다음부터 센다.
 */
public record LeaveRequestResponse(long id, long employeeId, String employeeName, String orgUnitName,
                                   long leaveTypeId, String leaveTypeName, int leaveYear, LocalDate startDate,
                                   LocalDate endDate, int days, LeaveStatus status, String reason, String cancelReason,
                                   OffsetDateTime createdAt, List<ApprovalStepView> approvalSteps) {
}
