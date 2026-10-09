package com.nexuslabs.hr.domain.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * GET · PUT /api/eval-cycles/{id}/targets — 대상 규칙(F-EVAL-03). 위에서부터(priority 작은 순) 처음 맞는 규칙의 템플릿을 쓴다.
 * 조건이 모두 null 이면 "모두", evalTemplateId 가 null 이면 평가 제외. 응답에는 이름이 더해진다.
 */
public record EvalTargetRules(@NotNull @Valid List<Rule> rules) {

    public record Rule(@NotNull Integer priority, Boolean condIsOrgLead, Long condJobTitleId, String condJobTitleName,
                       Long condJobGradeId, String condJobGradeName, Long condEmploymentTypeId,
                       String condEmploymentTypeName, Long evalTemplateId, String evalTemplateName) {
    }
}
