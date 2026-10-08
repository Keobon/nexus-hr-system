package com.nexuslabs.hr.domain.payroll.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 소득세 구간 전체(GET · PUT /api/tax-brackets, F-PAY-02). PUT 은 전체 교체 — 0원부터 빈틈 · 겹침 없이 이어지고
 * 마지막 구간만 upperBound 가 null 이어야 한다(아니면 TAX_BRACKET_INVALID). 구간은 [lowerBound, upperBound).
 */
public record TaxBrackets(@NotEmpty @Valid List<Bracket> brackets) {

    public record Bracket(@NotNull Long lowerBound, Long upperBound, @NotNull BigDecimal rate,
                          @NotNull Long progressiveDeduction) {
    }
}
