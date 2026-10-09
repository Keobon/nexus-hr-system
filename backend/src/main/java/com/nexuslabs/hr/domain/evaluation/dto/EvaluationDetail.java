package com.nexuslabs.hr.domain.evaluation.dto;

import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleStatus;
import com.nexuslabs.hr.domain.evaluation.entity.EvaluationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/evaluations/{id} — 입력 폼(F-EVAL-04). 항목별 질문 + 저장된 점수 + 종합의견 + 서버가 계산한 항목 점수 · 총점.
 * 점수는 그 항목 질문에 모두 답해야 나오고, 총점은 모든 항목 점수가 있을 때만 나온다(아니면 null).
 * editable = 지금 요청한 사람이 평가자이고 입력할 수 있는 상태다(작성 전 · 작성중이면서 기간 진행 중, 또는 재오픈).
 */
public record EvaluationDetail(long id, long cycleId, String cycleName, EvalCycleStatus cycleStatus, long targetId,
                               String targetName, String orgUnitName, long evaluatorId, String evaluatorName,
                               String templateName, EvaluationStatus status, String overallComment,
                               String reopenReason, OffsetDateTime submittedAt, OffsetDateTime confirmedAt,
                               boolean editable, List<Criteria> criteria, BigDecimal totalScore) {

    public record Criteria(long id, String category, String name, int weight, BigDecimal itemScore,
                           BigDecimal convertedScore, List<Question> questions) {
    }

    public record Question(long id, String content, Integer score) {
    }
}
