package com.nexuslabs.hr.domain.evaluation.dto;

import java.util.List;

/** 템플릿 상세(항목 → 질문 중첩). inUse = 시작된(진행 · 종료) 평가 기간에 쓰였다 — 그러면 수정할 수 없고 복사해서 고친다. */
public record EvalTemplateDetail(long id, String name, Long copiedFromId, boolean isActive, boolean inUse,
                                 List<Criteria> criteria) {

    public record Criteria(long id, String category, String name, int weight, int sortOrder,
                           List<Question> questions) {
    }

    public record Question(long id, String content, int sortOrder) {
    }
}
