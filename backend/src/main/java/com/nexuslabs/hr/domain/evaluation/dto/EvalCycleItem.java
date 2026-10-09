package com.nexuslabs.hr.domain.evaluation.dto;

import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 평가 기간 한 줄. evaluationCount 는 시작 때 만든 평가 건수(예정이면 0).
 * 만들기 · 수정 응답에서 다른 기간과 날짜가 겹치면 warning = "PERIOD_OVERLAP"(저장은 됐다), 그 밖에는 null.
 */
public record EvalCycleItem(long id, String name, LocalDate startDate, LocalDate endDate, EvalCycleStatus status,
                            OffsetDateTime startedAt, OffsetDateTime closedAt, long evaluationCount, String warning) {
}
