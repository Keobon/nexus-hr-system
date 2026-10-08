package com.nexuslabs.hr.domain.payroll.dto;

import java.time.LocalDate;

/** 명세서 목록 행(GET /api/payslips, PAYROLL_READ). orgUnitName 은 정산 당시 소속. */
public record PayslipRow(long id, String payMonth, LocalDate payDate, long employeeId, String employeeNo, String name,
                         String orgUnitName, long grossPay, long totalDeduction, long netPay, long companyBurdenTotal) {
}
