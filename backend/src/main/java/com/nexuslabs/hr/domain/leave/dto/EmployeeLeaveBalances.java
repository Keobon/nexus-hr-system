package com.nexuslabs.hr.domain.leave.dto;

import java.util.List;

/** GET /api/leave-balances 한 줄 — 직원 1명의 차감 종류별 잔여. */
public record EmployeeLeaveBalances(long employeeId, String employeeNo, String name, String orgUnitName,
                                    List<LeaveBalance> balances) {
}
