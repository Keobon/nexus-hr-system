package com.nexuslabs.hr.domain.leave.dto;

/** 휴가 종류 하나의 잔여(F-LEAVE-05). 잔여 = 부여 합계 − 사용(승인완료) − 승인대기(BR-LEAVE-003). */
public record LeaveBalance(long leaveTypeId, String leaveTypeName, int granted, int used, int pending, int remaining) {

    public static LeaveBalance of(long leaveTypeId, String leaveTypeName, int granted, int used, int pending) {
        return new LeaveBalance(leaveTypeId, leaveTypeName, granted, used, pending, granted - used - pending);
    }
}
