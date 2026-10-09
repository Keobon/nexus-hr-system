package com.nexuslabs.hr.domain.evaluation.dto;

import java.util.List;

/**
 * GET /api/eval-cycles/{id}/targets/preview — 지금 시작하면 만들어질 평가(targets)와 빠지는 사람(excluded).
 * 대상 후보는 재직 · 휴직 직원이고, 빠지는 이유는 ON_LEAVE(휴직) · NO_RULE(맞는 규칙 없음) · RULE_EXCLUDED(템플릿 없는 규칙)
 * · NO_EVALUATOR(평가자 없음 — 최상위 조직장 등). 사원번호 순.
 */
public record EvalTargetPreview(List<Target> targets, List<Excluded> excluded) {

    public record Target(long employeeId, String employeeNo, String name, String orgUnitName, int rulePriority,
                         long evalTemplateId, String evalTemplateName, long evaluatorId, String evaluatorName) {
    }

    public record Excluded(long employeeId, String employeeNo, String name, String orgUnitName, Reason reason) {
    }

    public enum Reason { ON_LEAVE, NO_RULE, RULE_EXCLUDED, NO_EVALUATOR }
}
