package com.nexuslabs.hr.domain.leave.dto;

/** 휴가 종류(F-LEAVE-01). */
public record LeaveTypeResponse(long id, String name, int annualDays, boolean deductsBalance, boolean paid,
                                boolean prorateFirstYear, Integer seniorityStartYears, Integer seniorityIntervalYears,
                                Integer seniorityAddDays, Integer seniorityMaxDays, int sortOrder, boolean active) {
}
