package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;

import java.time.LocalDate;

/** 정산 월 목록 행(GET /api/payroll-runs) — 최근 귀속 월부터. */
public record PayrollRunRow(long id, String payMonth, LocalDate payDate, PayrollStatus status, PayrollTotals totals) {
}
