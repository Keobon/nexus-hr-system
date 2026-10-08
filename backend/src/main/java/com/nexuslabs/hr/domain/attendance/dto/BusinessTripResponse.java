package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.entity.TripType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 출장 상세(GET /api/business-trips/{id}, 신청 · 철회 · 결과 보고 응답). approvalSteps 는 API 설계서 9장 형식.
 * expenseClaim 은 이 출장의 경비 청구 요약 — 진행 중(승인대기·승인완료) 청구가 있으면 그것, 없으면 가장 최근 청구, 없으면 null.
 */
public record BusinessTripResponse(long id, long employeeId, String employeeName, String orgUnitName, TripType tripType,
                                   String destination, String purpose, LocalDate startDate, LocalDate endDate,
                                   Long estimatedCost, RequestStatus status, String cancelReason, String reportText,
                                   OffsetDateTime reportedAt, OffsetDateTime createdAt, ExpenseClaimSummary expenseClaim,
                                   List<ApprovalStepView> approvalSteps) {

    /** settledPayMonth = 반영된 정산의 귀속 월("2026-10"), 아직 반영 전이면 null. */
    public record ExpenseClaimSummary(long id, RequestStatus status, long totalAmount, String settledPayMonth) {
    }
}
