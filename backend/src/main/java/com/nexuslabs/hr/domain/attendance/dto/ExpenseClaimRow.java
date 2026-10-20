package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;

import java.time.OffsetDateTime;

/**
 * 경비 청구 목록 행(GET /api/me/expense-claims · /api/expense-claims) — 출장 · 합계 · 상태 · 정산 반영 월.
 * currentStep 은 API 설계서 8장 "휴가 신청 목록 행"과 같다.
 * rejectReason 은 반려된 신청만 반려한 단계의 의견, 그 밖에는 null 이다(역할 분담 2.3 L-94 — 휴가 목록 행과 같다).
 */
public record ExpenseClaimRow(long id, long businessTripId, String destination, long employeeId, String employeeName,
                              String orgUnitName, long totalAmount, int lineCount, RequestStatus status,
                              String settledPayMonth, OffsetDateTime createdAt, CurrentStep currentStep,
                              String rejectReason) {

    public ExpenseClaimRow withApproval(CurrentStep step, String rejectReason) {
        return new ExpenseClaimRow(id, businessTripId, destination, employeeId, employeeName, orgUnitName, totalAmount,
                lineCount, status, settledPayMonth, createdAt, step, rejectReason);
    }
}
