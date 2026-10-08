package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.entity.TripType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 출장 목록 행(GET /api/me/business-trips · /api/business-trips). 화면 버튼 규칙(프론트 가이드 3.2)에 쓰는
 * reportWritten(결과 보고 작성 여부) · expenseClaimStatus(상세의 expenseClaim 과 같은 청구의 상태, 없으면 null)를 담는다.
 * currentStep 은 API 설계서 8장 "휴가 신청 목록 행"과 같다.
 */
public record BusinessTripRow(long id, long employeeId, String employeeName, String orgUnitName, TripType tripType,
                              String destination, LocalDate startDate, LocalDate endDate, Long estimatedCost,
                              RequestStatus status, boolean reportWritten, RequestStatus expenseClaimStatus,
                              OffsetDateTime createdAt, CurrentStep currentStep) {

    public BusinessTripRow withCurrentStep(CurrentStep step) {
        return new BusinessTripRow(id, employeeId, employeeName, orgUnitName, tripType, destination, startDate, endDate,
                estimatedCost, status, reportWritten, expenseClaimStatus, createdAt, step);
    }
}
