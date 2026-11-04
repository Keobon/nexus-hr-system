package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 과세액 대비 요율 항목의 요율 · 기준 상한 · 하한 이력. append-only. 정산 때는 귀속 월 말일에 유효한 값을 쓴다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayItemRate extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long payItemId;
    private BigDecimal employeeRate;
    private BigDecimal companyRate;
    private Long baseUpperLimit;
    private Long baseLowerLimit;
    private LocalDate effectiveFrom;
    private Long createdBy;

    public PayItemRate(Long payItemId, BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit,
                       Long baseLowerLimit, LocalDate effectiveFrom, Long createdBy) {
        this.payItemId = payItemId;
        this.employeeRate = employeeRate;
        this.companyRate = companyRate;
        this.baseUpperLimit = baseUpperLimit;
        this.baseLowerLimit = baseLowerLimit;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
    }
}
