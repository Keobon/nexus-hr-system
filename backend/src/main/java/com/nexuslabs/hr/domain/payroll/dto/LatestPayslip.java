package com.nexuslabs.hr.domain.payroll.dto;

/** 홈 me.latestPayslip(역할 분담 v2 2.1) — 가장 최근 귀속 월의 내 명세서. */
public record LatestPayslip(long id, String payMonth, long netPay) {
}
