package com.nexuslabs.hr.domain.evaluation.dto;

/** 템플릿 목록 한 줄. */
public record EvalTemplateItem(long id, String name, Long copiedFromId, boolean isActive, boolean inUse,
                               int criteriaCount, int questionCount) {
}
