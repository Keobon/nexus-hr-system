package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayRuleTarget;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * POST /api/pay-items/{id}/rules 본문. 부서면 orgUnitId(필수) · includeSubOrgs(기본 true), 직급이면 jobGradeId(필수) ·
 * gradeOrAbove(기본 false). amount null = 그 대상의 규칙 해제, 0 = 그 대상은 받지 않음.
 */
public record PayItemRuleRequest(@NotNull LocalDate effectiveFrom, @NotNull PayRuleTarget targetType, Long orgUnitId,
                                 Boolean includeSubOrgs, Long jobGradeId, Boolean gradeOrAbove, Long amount) {
}
