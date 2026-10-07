package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 소득세 구간(BR-PAY-007). upperBound 가 없으면 마지막 구간이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxBracket extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long lowerBound;
    private Long upperBound;
    private BigDecimal rate;
    private long progressiveDeduction;

    public TaxBracket(long lowerBound, Long upperBound, BigDecimal rate, long progressiveDeduction) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.rate = rate;
        this.progressiveDeduction = progressiveDeduction;
    }
}
