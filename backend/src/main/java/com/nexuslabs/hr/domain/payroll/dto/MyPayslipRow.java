package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;

import java.time.LocalDate;

/** 내 명세서 목록 행(GET /api/me/payslips) — 최근 귀속 월부터. */
public record MyPayslipRow(long id, String payMonth, LocalDate payDate, PayrollStatus status, long netPay) {
}
