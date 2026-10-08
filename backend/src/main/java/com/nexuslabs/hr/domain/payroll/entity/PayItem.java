package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** 급여 항목. 수정하면 다음 정산부터 적용된다(명세서는 스냅샷). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pay_item_kind")
    private PayItemKind itemKind;

    @Column(name = "is_taxable")
    private boolean taxable = true;

    private Long nonTaxableLimit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pay_calc_method")
    private PayCalcMethod calcMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pay_apply_to")
    private PayApplyTo applyTo = PayApplyTo.ALL;

    private Long defaultAmount;
    private BigDecimal baseRate;
    private BigDecimal employeeRate;
    private BigDecimal companyRate;
    private Long baseUpperLimit;
    private Long baseLowerLimit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "attendance_basis")
    private AttendanceBasis attendanceBasis;

    private BigDecimal multiplier;
    private boolean inOrdinaryWage = false;
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;

    public PayItem(String name, PayItemKind itemKind, PayCalcMethod calcMethod, int sortOrder) {
        this.name = name;
        this.itemKind = itemKind;
        this.calcMethod = calcMethod;
        this.sortOrder = sortOrder;
    }

    /** 등록 · 수정 — 계산 방식별로 맞춘 값(PayItemService)을 통째로 넣는다. 수정은 다음 정산부터 쓰인다. */
    public void apply(String name, PayItemKind itemKind, boolean taxable, Long nonTaxableLimit, PayCalcMethod calcMethod,
                      PayApplyTo applyTo, Long defaultAmount, BigDecimal baseRate, BigDecimal employeeRate,
                      BigDecimal companyRate, Long baseUpperLimit, Long baseLowerLimit, AttendanceBasis attendanceBasis,
                      BigDecimal multiplier, boolean inOrdinaryWage, int sortOrder, boolean active) {
        this.name = name;
        this.itemKind = itemKind;
        this.taxable = taxable;
        this.nonTaxableLimit = nonTaxableLimit;
        this.calcMethod = calcMethod;
        this.applyTo = applyTo;
        this.defaultAmount = defaultAmount;
        this.baseRate = baseRate;
        this.employeeRate = employeeRate;
        this.companyRate = companyRate;
        this.baseUpperLimit = baseUpperLimit;
        this.baseLowerLimit = baseLowerLimit;
        this.attendanceBasis = attendanceBasis;
        this.multiplier = multiplier;
        this.inOrdinaryWage = inOrdinaryWage;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    /** 쓰인 항목은 지우지 않고 다음 정산부터 빠지게 한다(BR-ORG-001). */
    public void deactivate() {
        this.active = false;
    }
}
