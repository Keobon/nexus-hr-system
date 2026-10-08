package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.SalaryType;

import java.time.LocalDate;

/**
 * 직원 급여 목록 행(GET /api/salaries). 미등록이면 registered=false 이고 급여 값과 fixedItemsTotal 은 null.
 * fixedItemsTotal = 오늘 적용되는 고정액 지급(전원 기본 금액 + 지정 직원 금액) + 기본급 대비 % 지급 — 근태 연동 · 수동 · 공제 제외.
 */
public record SalaryRow(long employeeId, String employeeNo, String name, String orgUnitName, boolean registered,
                        SalaryType salaryType, Long annualSalary, Long monthlyBase, LocalDate effectiveFrom,
                        Long fixedItemsTotal) {
}
