package com.nexuslabs.hr.domain.leave.service;

/** 부여일수 계산에 필요한 휴가 종류 값(leave_type 의 일부). 근속 가산 4개는 모두 있거나 모두 null. */
public record LeaveTypeRule(int annualDays, boolean prorateFirstYear, Integer seniorityStartYears,
                            Integer seniorityIntervalYears, Integer seniorityAddDays, Integer seniorityMaxDays) {

    public boolean hasSeniority() {
        return seniorityStartYears != null;
    }
}
