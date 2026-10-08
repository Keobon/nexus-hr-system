package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.attendance.service.TodayStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 홈 출퇴근 버튼용 오늘 근태(GET /api/me/attendance/today, API 설계서 7.1). 출근 · 퇴근 · 근무 형태 변경도 이 모양으로 응답한다.
 * workDate 는 이 응답이 보여 주는 근태의 날짜다 — 자정을 넘겨 어제 근무가 이어지는 중이면 어제 날짜가 온다.
 * 값이 없는 필드는 null 로 내려간다.
 */
public record TodayAttendance(LocalDate workDate, boolean isWorkday, TodayStatus status, WorkType workType,
                              String placeMemo, OffsetDateTime checkInAt, OffsetDateTime checkOutAt) {
}
