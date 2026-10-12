package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 연장근무 목록 행(GET /api/me/overtime-requests · /api/overtime-requests). currentStep 은 API 설계서 8장
 * "휴가 신청 목록 행"과 같다 — 모든 단계가 생략돼 즉시 승인된 건은 null(화면은 "자동 승인").
 * holiday = 근무일이 아닌 날의 신청, nightMinutes = 예정 시간 중 야간 시간대와 겹치는 분(역할 분담 2.3 M 102).
 */
public record OvertimeRow(long id, long employeeId, String employeeName, String orgUnitName, LocalDate workDate,
                          OffsetDateTime plannedStart, OffsetDateTime plannedEnd, int requestedMinutes,
                          Integer approvedMinutes, boolean holiday, int nightMinutes, String reason,
                          RequestStatus status, OffsetDateTime createdAt, CurrentStep currentStep) {

    public OvertimeRow withCurrentStep(CurrentStep step) {
        return new OvertimeRow(id, employeeId, employeeName, orgUnitName, workDate, plannedStart, plannedEnd,
                requestedMinutes, approvedMinutes, holiday, nightMinutes, reason, status, createdAt, step);
    }
}
