package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayApplyTo;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;
import com.nexuslabs.hr.domain.payroll.entity.SalaryType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 한 직원의 급여(GET /api/employees/{id}/salaries · /api/me/salaries, F-PAY-04). current · currentAmount 는 오늘 유효한 값
 * (같은 적용 시작일이면 나중 행). 이력은 최신순이고 미래 적용 행도 들어 있다.
 * payItems = 오늘 적용되는 전원 고정액 · 기본급 대비 % 항목 + 이 직원에게 지정된 적 있는 지정 직원 항목.
 */
public record EmployeeSalaries(long employeeId, boolean payrollEligible, Current current, List<SalaryEntry> salaries,
                               List<PayItemLine> payItems) {

    public record Current(SalaryType salaryType, Long annualSalary, long monthlyBase, LocalDate effectiveFrom) {
    }

    public record SalaryEntry(long id, SalaryType salaryType, Long annualSalary, long monthlyBase,
                              LocalDate effectiveFrom, String reason, String createdByName, OffsetDateTime createdAt) {
    }

    /** currentAmount: 고정액(전원)은 기본 금액, 기본급 대비 %는 ⌊현재 월 기본급 × 요율 ÷ 100⌋(급여 없으면 null), 지정 직원은 오늘 유효한 금액. */
    public record PayItemLine(long payItemId, String name, PayItemKind itemKind, PayCalcMethod calcMethod,
                              PayApplyTo applyTo, Long currentAmount, List<PayItemEntry> history) {
    }

    public record PayItemEntry(long id, long amount, LocalDate effectiveFrom, String reason, String createdByName,
                               OffsetDateTime createdAt) {
    }
}
