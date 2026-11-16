package com.nexuslabs.hr.domain.assistant.service;

import java.util.List;
import java.util.Objects;

/**
 * LLM 이 돌려주는 JSON 하나 — { leaveTypeName, startDate, endDate, halfDay, reason, missing[], question }.
 * 날짜는 YYYY-MM-DD 문자열, halfDay 는 AM · PM · NONE(종일). null 은 "이번 문장으로는 모름(지금 초안 값 유지)".
 * missing 은 아직 정할 수 없는 필드 이름. 모든 값은 서버가 다시 검사한다.
 */
public record LeaveExtraction(String leaveTypeName, String startDate, String endDate, String halfDay, String reason,
                              List<String> missing, String question) {

    /** 어긋난 응답(missing 이 null 이거나 null 원소가 섞임)도 merge 에서 터지지 않게 빈 목록 · null 제외로 맞춘다. */
    public LeaveExtraction {
        missing = missing == null ? List.of() : missing.stream().filter(Objects::nonNull).toList();
    }
}
