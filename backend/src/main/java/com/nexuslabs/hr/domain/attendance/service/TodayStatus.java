package com.nexuslabs.hr.domain.attendance.service;

/**
 * 오늘 상태(기능명세서 15.4). 화면 표시용이라 저장하지 않고 조회할 때 계산한다 — DB ENUM 이 아니다.
 * 출근 중이면 근무 형태(OFFICE · REMOTE · FIELD · BUSINESS_TRIP)가 그대로 상태가 된다.
 */
public enum TodayStatus {
    BEFORE_WORK, OFFICE, REMOTE, FIELD, BUSINESS_TRIP, ON_VACATION, ON_LEAVE, OFF_WORK
}
