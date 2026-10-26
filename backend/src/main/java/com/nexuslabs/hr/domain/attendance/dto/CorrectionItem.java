package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 정정 대상 한 건(GET /api/attendances/corrections, F-ATT-04).
 * status 는 저장된 값 그대로다 — 오늘 이전인데 아직 출근 상태인 행은 type 이 MISSING_CHECKOUT 이고 status 는 CHECKED_IN 이다.
 * conflict 는 겹친 휴가 · 출장이고 퇴근미기록이면 null 이다.
 * EXPLANATION 은 대기 중인 근태 소명(F-ATT-10)이다 — explanation 에 직원 사유와 요청 값이 있고, 결근 소명이면 근태 행이 없어
 * attendanceId · status · 출퇴근 시각이 null 이다. 같은 날 퇴근미기록 행과 따로 한 건씩 나온다.
 */
public record CorrectionItem(Type type, Long attendanceId, long employeeId, String employeeNo, String name,
                             String orgUnitName, LocalDate workDate, AttendanceStatus status, WorkType workType,
                             OffsetDateTime checkInAt, OffsetDateTime checkOutAt, Conflict conflict,
                             Explanation explanation) {

    public enum Type { MISSING_CHECKOUT, LEAVE_CONFLICT, TRIP_CONFLICT, EXPLANATION }

    /** 직원이 낸 소명 — checkInAt · checkOutAt · workType 은 요청 값(없으면 null). */
    public record Explanation(long id, String reason, OffsetDateTime checkInAt, OffsetDateTime checkOutAt,
                              WorkType workType, OffsetDateTime createdAt) {
    }

    /** id 는 휴가 신청 또는 출장 ID, name 은 휴가 종류 또는 출장지. */
    public record Conflict(long id, String name, LocalDate startDate, LocalDate endDate) {
    }
}
