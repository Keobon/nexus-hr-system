package com.nexuslabs.hr.domain.attendance.entity;

/** DB ENUM attendance_status 과 이름이 같아야 한다. */
public enum AttendanceStatus {
    CHECKED_IN, CHECKED_OUT, MISSING_CHECKOUT, ON_VACATION, ON_BUSINESS_TRIP
}
