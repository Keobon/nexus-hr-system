package com.nexuslabs.hr.domain.payroll.dto;

/** 정산 합계 — 명세서를 더한 값(저장하지 않는다, ERD 7장). */
public record PayrollTotals(int headcount, long grossPay, long totalDeduction, long netPay, long companyBurdenTotal) {
}
