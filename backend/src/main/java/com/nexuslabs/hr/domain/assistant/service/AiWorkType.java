package com.nexuslabs.hr.domain.assistant.service;

/** 자유 입력 문장의 업무 분류(POST /api/me/ai/route) — 챗봇이 어느 초안 API 로 보낼지 고른다. */
public enum AiWorkType {
    LEAVE, OVERTIME, BUSINESS_TRIP, UNKNOWN;

    /** LLM 이 준 값 — 모르는 값은 UNKNOWN. */
    public static AiWorkType of(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
