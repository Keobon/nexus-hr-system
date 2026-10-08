package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 정정(PATCH /api/attendances/{id}) · 근태 생성(POST /api/attendances)의 응답.
 * 그 날짜의 귀속 월이 이미 정산됐으면 warning 이 "PAY_MONTH_SETTLED" 다 — 정정은 됐고 명세서는 바뀌지 않는다(BR-PAY-011).
 */
public record AttendanceCorrectionResponse(long attendanceId, long employeeId, LocalDate workDate,
                                           AttendanceStatus status, WorkType workType, OffsetDateTime checkInAt,
                                           OffsetDateTime checkOutAt, String correctionReason,
                                           OffsetDateTime correctedAt, String warning) {
}
