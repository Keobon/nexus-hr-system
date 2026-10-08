package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayVarCode;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/pay-variables 본문(F-PAY-02). 값 범위는 변수마다 다르다 — PayVariableService. */
public record PayVariableRequest(@NotNull PayVarCode varCode, @NotNull BigDecimal value,
                                 @NotNull LocalDate effectiveFrom) {
}
