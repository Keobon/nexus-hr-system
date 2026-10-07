package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** 명세서 항목별 줄. 명세서와 함께 만들고 바꾸지 않는다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaystubLine extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paystub_id")
    private Paystub paystub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_item_id")
    private PayItem payItem;

    private String itemNameSnap;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pay_item_kind")
    private PayItemKind itemKind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pay_calc_method")
    private PayCalcMethod calcMethod;

    private long amount;
    private long taxableAmount;
    private long nonTaxableAmount;
    private Long companyAmount;
    private BigDecimal quantity;
    private Long unitPrice;
    private String formulaNote;
    private int sortOrder;

    public PaystubLine(Paystub paystub, PayItem payItem, String itemNameSnap, PayItemKind itemKind,
                       PayCalcMethod calcMethod, long amount, long taxableAmount, long nonTaxableAmount,
                       Long companyAmount, BigDecimal quantity, Long unitPrice, String formulaNote, int sortOrder) {
        this.paystub = paystub;
        this.payItem = payItem;
        this.itemNameSnap = itemNameSnap;
        this.itemKind = itemKind;
        this.calcMethod = calcMethod;
        this.amount = amount;
        this.taxableAmount = taxableAmount;
        this.nonTaxableAmount = nonTaxableAmount;
        this.companyAmount = companyAmount;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.formulaNote = formulaNote;
        this.sortOrder = sortOrder;
    }
}
