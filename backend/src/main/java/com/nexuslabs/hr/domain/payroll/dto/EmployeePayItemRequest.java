package com.nexuslabs.hr.domain.payroll.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** POST /api/employees/{id}/pay-items 본문(F-PAY-03) — "지정 직원" 고정액 항목의 직원별 금액. 적용을 끝낼 때는 amount 0. */
public record EmployeePayItemRequest(
        @NotNull Long payItemId,
        @NotNull @PositiveOrZero Long amount,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 255) String reason) {
}
