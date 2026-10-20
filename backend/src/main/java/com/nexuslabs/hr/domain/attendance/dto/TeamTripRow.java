package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.entity.TripType;

import java.time.LocalDate;

/**
 * 부서원 출장 일정 한 줄(GET /me/team-trips, 역할 분담 2.3 M-101). 목적 · 비용 · 결재 정보는 넣지 않는다.
 * id 는 화면 목록 키 — 상세(GET /business-trips/{id})는 따로 권한을 본다.
 */
public record TeamTripRow(long id, long employeeId, String employeeName, TripType tripType, String destination,
                          LocalDate startDate, LocalDate endDate, RequestStatus status) {
}
