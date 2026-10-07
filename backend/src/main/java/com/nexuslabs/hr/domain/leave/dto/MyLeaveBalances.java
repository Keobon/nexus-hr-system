package com.nexuslabs.hr.domain.leave.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /api/me/leave-balances — 휴가 연도 기간과 차감 종류별 잔여. */
public record MyLeaveBalances(int leaveYear, LocalDate startDate, LocalDate endDate, List<LeaveBalance> balances) {
}
