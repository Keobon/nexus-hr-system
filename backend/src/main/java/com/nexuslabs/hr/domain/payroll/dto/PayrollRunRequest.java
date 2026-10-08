package com.nexuslabs.hr.domain.payroll.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * POST /api/payroll-runs/preview · /api/payroll-runs 본문(F-PAY-05). 확정은 미리보기와 같은 입력을 그대로 보낸다.
 * payDate 를 비우면 귀속 월 다음 달의 회사 기본 급여 지급일(그달 말일보다 크면 말일). manualInputs 는 수동 입력 항목만.
 */
public record PayrollRunRequest(@NotNull YearMonth payMonth, LocalDate payDate, @Valid List<ManualInput> manualInputs) {

    public record ManualInput(@NotNull Long employeeId, @NotNull Long payItemId, @NotNull @PositiveOrZero Long amount) {
    }
}
