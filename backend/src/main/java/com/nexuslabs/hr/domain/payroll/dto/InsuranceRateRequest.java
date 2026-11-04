package com.nexuslabs.hr.domain.payroll.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** POST /api/pay-items/insurance-rates 본문. 같은 적용 시작일로 여러 항목을 한 번에 저장한다. 값 범위는 PayItemRateService. */
public record InsuranceRateRequest(@NotNull LocalDate effectiveFrom, @NotEmpty List<Item> items) {

    public record Item(Long payItemId, BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit,
                       Long baseLowerLimit) {
    }
}
