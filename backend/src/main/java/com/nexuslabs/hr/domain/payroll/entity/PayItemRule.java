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

import java.time.LocalDate;

/**
 * 고정액 항목의 부서 · 직급 규칙 이력(2026-11-06). append-only — 금액을 바꾸거나 규칙을 없앨 때도 새 행을 쌓는다(amount null = 해제).
 * 같은 대상(항목 · 대상 종류 · 부서/직급 · 하위 포함/이상)의 행 중 그날 이전 최신 행이 유효 값이다.
 */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayItemRule extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long payItemId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pay_rule_target")
    private PayRuleTarget targetType;

    private Long orgUnitId;
    private boolean includeSubOrgs;
    private Long jobGradeId;
    private boolean gradeOrAbove;
    private Long amount;
    private LocalDate effectiveFrom;
    private Long createdBy;

    public PayItemRule(Long payItemId, PayRuleTarget targetType, Long orgUnitId, boolean includeSubOrgs, Long jobGradeId,
                       boolean gradeOrAbove, Long amount, LocalDate effectiveFrom, Long createdBy) {
        this.payItemId = payItemId;
        this.targetType = targetType;
        this.orgUnitId = orgUnitId;
        this.includeSubOrgs = includeSubOrgs;
        this.jobGradeId = jobGradeId;
        this.gradeOrAbove = gradeOrAbove;
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
    }
}
