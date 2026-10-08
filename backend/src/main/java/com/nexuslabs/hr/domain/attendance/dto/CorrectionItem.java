package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 정정 대상 한 건(GET /api/attendances/corrections, F-ATT-04).
 * status 는 저장된 값 그대로다 — 오늘 이전인데 아직 출근 상태인 행은 type 이 MISSING_CHECKOUT 이고 status 는 CHECKED_IN 이다.
 * conflict 는 겹친 휴가 · 출장이고 퇴근미기록이면 null 이다.
 */
public record CorrectionItem(Type type, long attendanceId, long employeeId, String employeeNo, String name,
                             String orgUnitName, LocalDate workDate, AttendanceStatus status, WorkType workType,
                             OffsetDateTime checkInAt, OffsetDateTime checkOutAt, Conflict conflict) {

    public enum Type { MISSING_CHECKOUT, LEAVE_CONFLICT, TRIP_CONFLICT }

    /** id 는 휴가 신청 또는 출장 ID, name 은 휴가 종류 또는 출장지. */
    public record Conflict(long id, String name, LocalDate startDate, LocalDate endDate) {
    }
}
