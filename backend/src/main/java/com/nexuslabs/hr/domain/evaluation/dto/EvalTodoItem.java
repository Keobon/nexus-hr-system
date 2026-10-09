package com.nexuslabs.hr.domain.evaluation.dto;

import com.nexuslabs.hr.domain.evaluation.entity.EvaluationStatus;

import java.time.LocalDate;

/** GET /api/me/evaluations/todo 한 줄 — 내가 평가자인 건(진행 중 기간 전부 + 재오픈). */
public record EvalTodoItem(long evaluationId, long cycleId, String cycleName, LocalDate cycleEndDate, long targetId,
                           String employeeNo, String targetName, String orgUnitName, String templateName,
                           EvaluationStatus status) {
}
