package com.nexuslabs.hr.domain.assistant.entity;

/** AI 휴가 결재 초안 상태 — DB ENUM ai_draft_status 와 이름이 같아야 한다. */
public enum AiDraftStatus {
    /** 만드는 중(되묻는 중 포함). 확인 · 폐기 · 수정할 수 있다. */
    DRAFT,
    /** 직원이 확인해서 휴가 신청이 생겼다. */
    CONFIRMED,
    DISCARDED,
    /** 30분이 지나 확인할 수 없다. */
    EXPIRED
}
