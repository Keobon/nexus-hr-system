package com.nexuslabs.hr.domain.evaluation.dto;

import com.nexuslabs.hr.domain.evaluation.entity.EvaluationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * GET /api/eval-cycles/{id}/progress (F-EVAL-05, API 12.1 2026-10-08) — 상태별 건수와 대상자별 상태.
 * totalScore 는 제출완료 · 확정일 때만, 그 밖(재오픈 포함)은 null. 미제출 목록은 화면이 items 에서 걸러 낸다.
 */
public record EvalProgress(Map<EvaluationStatus, Long> counts, List<Item> items) {

    public record Item(long evaluationId, long targetId, String targetName, String orgUnitName, String evaluatorName,
                       String templateName, EvaluationStatus status, BigDecimal totalScore) {
    }
}
