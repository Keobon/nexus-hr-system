package com.nexuslabs.hr.domain.assistant.dto;

/**
 * 연장근무 · 출장 초안 응답의 status — 휴가 초안과 같은 뜻.
 * READY 확인 카드 · NEED_INFO question 으로 되묻기 · INVALID 값은 모였지만 이대로는 신청 불가(errors).
 */
public enum AiDraftResult {
    READY, NEED_INFO, INVALID
}
