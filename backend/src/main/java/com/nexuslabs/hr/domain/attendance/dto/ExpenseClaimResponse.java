package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.global.file.StoredFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 경비 청구 상세(GET /api/expense-claims/{id}, 청구 201 · 철회 응답). API 설계서 7.3 청구 응답 예시의
 * id · status · totalAmount · approvalSteps 를 포함한다. settledPayMonth = 반영된 정산의 귀속 월, 반영 전이면 null.
 */
public record ExpenseClaimResponse(long id, long businessTripId, String destination, LocalDate tripStartDate,
                                   LocalDate tripEndDate, long employeeId, String employeeName, String orgUnitName,
                                   RequestStatus status, String cancelReason, long totalAmount, String settledPayMonth,
                                   OffsetDateTime createdAt, List<Line> lines, List<ApprovalStepView> approvalSteps) {

    /** receiptFile 은 영수증이 없으면 null. 내려받기는 GET /files/{id}. */
    public record Line(long id, long expenseTypeId, String expenseTypeName, LocalDate usedDate, long amount,
                       String description, StoredFile.FileInfo receiptFile) {
    }
}
