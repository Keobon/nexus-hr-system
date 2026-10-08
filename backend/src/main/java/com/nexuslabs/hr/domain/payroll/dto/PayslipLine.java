package com.nexuslabs.hr.domain.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 명세서 줄(API 설계서 10.3). 지급 · 공제를 항목 정렬 순서로 한 배열에 담는다. 근태 연동 줄만 quantity(시간) · unitPrice ·
 * formulaNote 가 있다. companyAmount(회사부담)는 과세액 대비 요율 줄만 값이 있고, 본인 명세서에서는 필드 자체가 빠진다
 * (Java null = 필드 없음, Optional.empty = null — F-PAY-06).
 */
public record PayslipLine(long payItemId, String name, PayItemKind itemKind, PayCalcMethod calcMethod, long amount,
                          long taxableAmount, long nonTaxableAmount,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Optional<Long> companyAmount,
                          BigDecimal quantity, Long unitPrice, String formulaNote) {

    /** 본인 명세서 — 회사부담을 뺀다. */
    public PayslipLine withoutCompanyAmount() {
        return new PayslipLine(payItemId, name, itemKind, calcMethod, amount, taxableAmount, nonTaxableAmount, null,
                quantity, unitPrice, formulaNote);
    }
}
