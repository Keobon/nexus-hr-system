package com.nexuslabs.hr.domain.payroll.dto;

import java.util.List;

/** 미리보기의 직원 한 명(API 설계서 10.3 "미리보기 응답 — 직원 1명"). attendance 는 그달 근태 합계(분 · 일 · 횟수). */
public record EmployeePayroll(long employeeId, String employeeNo, String name, String orgUnitName, Attendance attendance,
                              long basePay, long ordinaryHourlyWage, int workedDays, int monthDays,
                              List<PayslipLine> lines, long grossPay, long taxablePay, int dependentsCount,
                              int childrenCount, long incomeTax, long localIncomeTax, long totalDeduction, long netPay,
                              long companyBurdenTotal, List<Long> expenseClaimIds) {

    /** absenceMinutes = 결근한 날마다 그날 근무시간의 소정근로분을 더한 값(결근 공제의 근거 시간). */
    public record Attendance(int overtimeMinutes, int nightMinutes, int holidayMinutes, int holidayOvertimeMinutes,
                             int absentDays, int absenceMinutes, int lateCount) {
    }
}
