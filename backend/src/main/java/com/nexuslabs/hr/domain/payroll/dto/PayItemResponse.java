package com.nexuslabs.hr.domain.payroll.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.payroll.entity.AttendanceBasis;
import com.nexuslabs.hr.domain.payroll.entity.PayApplyTo;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;

import java.math.BigDecimal;

/**
 * 급여 항목 한 건(API 설계서 10.1). 요율 · 배율은 퍼센트 · 소수 그대로(4.5, 1.5 — 끝의 0은 뗀다).
 * inUse = 명세서 줄이나 직원별 항목 이력이 있다 — 구분 · 계산 방식 · 적용 대상을 바꿀 수 없고 삭제하면 비활성화된다.
 */
public record PayItemResponse(long id, String name, PayItemKind itemKind, @JsonProperty("isTaxable") boolean taxable,
                              Long nonTaxableLimit, PayCalcMethod calcMethod, PayApplyTo applyTo, Long defaultAmount,
                              BigDecimal baseRate, BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit,
                              Long baseLowerLimit, AttendanceBasis attendanceBasis, BigDecimal multiplier,
                              boolean inOrdinaryWage, int sortOrder, @JsonProperty("isActive") boolean active,
                              boolean inUse) {

    public static PayItemResponse from(PayItem item, boolean inUse) {
        return new PayItemResponse(item.getId(), item.getName(), item.getItemKind(), item.isTaxable(),
                item.getNonTaxableLimit(), item.getCalcMethod(), item.getApplyTo(), item.getDefaultAmount(),
                plain(item.getBaseRate()), plain(item.getEmployeeRate()), plain(item.getCompanyRate()),
                item.getBaseUpperLimit(), item.getBaseLowerLimit(), item.getAttendanceBasis(),
                plain(item.getMultiplier()), item.isInOrdinaryWage(), item.getSortOrder(), item.isActive(), inUse);
    }

    /** DECIMAL(7,4) 의 4.5000 → 4.5, 10.0000 → 10 (지수 표기 없이). */
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
