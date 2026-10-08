package com.nexuslabs.hr.domain.payroll.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.payroll.entity.AttendanceBasis;
import com.nexuslabs.hr.domain.payroll.entity.PayApplyTo;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/pay-items 본문(F-PAY-01). 계산 방식마다 쓰는 값이 다르다 — API 설계서 10.1 "계산 방식별 입력" 표.
 * 해당 없는 값은 무시하고 null 로 저장한다. PATCH 는 본문을 Map 으로 받아 현재 값에 병합한 뒤 같은 규칙으로 검사한다.
 * 비운 선택 값: isTaxable = 출장 경비면 false 아니면 true, applyTo = ALL, inOrdinaryWage = false, sortOrder = 맨 뒤, isActive = true.
 */
public record PayItemRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull PayItemKind itemKind,
        @JsonProperty("isTaxable") Boolean taxable,
        Long nonTaxableLimit,
        @NotNull PayCalcMethod calcMethod,
        PayApplyTo applyTo,
        Long defaultAmount,
        BigDecimal baseRate,
        BigDecimal employeeRate,
        BigDecimal companyRate,
        Long baseUpperLimit,
        Long baseLowerLimit,
        AttendanceBasis attendanceBasis,
        BigDecimal multiplier,
        Boolean inOrdinaryWage,
        Integer sortOrder,
        @JsonProperty("isActive") Boolean active) {
}
