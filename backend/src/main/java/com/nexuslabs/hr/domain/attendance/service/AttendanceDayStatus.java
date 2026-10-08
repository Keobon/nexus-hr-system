package com.nexuslabs.hr.domain.attendance.service;

/**
 * 근태 조회에서 보여 주는 하루의 상태(기능명세서 15.3). 앞의 다섯은 저장된 근태 상태와 같고,
 * ON_LEAVE(휴직) · NOT_RECORDED(미기록) · ABSENT(결근)는 근태 기록이 없는 근무일에 조회할 때 채운다 — DB ENUM 이 아니다.
 */
public enum AttendanceDayStatus {
    CHECKED_IN, CHECKED_OUT, MISSING_CHECKOUT, ON_VACATION, ON_BUSINESS_TRIP, ON_LEAVE, NOT_RECORDED, ABSENT
}
