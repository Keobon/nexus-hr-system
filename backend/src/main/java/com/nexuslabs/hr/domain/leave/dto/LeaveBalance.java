package com.nexuslabs.hr.domain.leave.dto;

import com.nexuslabs.hr.global.response.DaysJson;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;

/**
 * 휴가 종류 하나의 잔여(F-LEAVE-05). 잔여 = 부여 합계 − 사용(승인완료) − 승인대기(BR-LEAVE-003).
 * 반차가 있으면 0.5일 단위(2026-11-15) — 정수면 지금처럼 정수로 쓴다(DaysJson).
 */
public record LeaveBalance(long leaveTypeId, String leaveTypeName,
                           @JsonSerialize(using = DaysJson.class) BigDecimal granted,
                           @JsonSerialize(using = DaysJson.class) BigDecimal used,
                           @JsonSerialize(using = DaysJson.class) BigDecimal pending,
                           @JsonSerialize(using = DaysJson.class) BigDecimal remaining) {

    public static LeaveBalance of(long leaveTypeId, String leaveTypeName, BigDecimal granted, BigDecimal used,
                                  BigDecimal pending) {
        return new LeaveBalance(leaveTypeId, leaveTypeName, granted, used, pending,
                granted.subtract(used).subtract(pending));
    }
}
