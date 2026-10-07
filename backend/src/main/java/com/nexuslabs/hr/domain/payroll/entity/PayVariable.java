package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.global.entity.CreatedAtEntity;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 계산 변수 이력. append-only. 정산 때는 귀속 월 말일에 유효한 값을 쓴다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayVariable extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pay_var_code")
    private PayVarCode varCode;

    private BigDecimal value;
    private LocalDate effectiveFrom;
    private Long createdBy;

    public PayVariable(PayVarCode varCode, BigDecimal value, LocalDate effectiveFrom, Long createdBy) {
        this.varCode = varCode;
        this.value = value;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
    }
}
