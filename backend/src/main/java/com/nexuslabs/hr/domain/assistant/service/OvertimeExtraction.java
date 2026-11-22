package com.nexuslabs.hr.domain.assistant.service;

import java.util.List;
import java.util.Objects;

/**
 * 연장근무 문장에서 뽑은 값 — { workDate, plannedStart, plannedEnd, reason, missing[], question }.
 * 날짜는 YYYY-MM-DD, 시각은 HH:mm(24시간). null 은 "이번 문장으로는 모름(지금 초안 값 유지)". 모든 값은 서버가 다시 검사한다.
 */
public record OvertimeExtraction(String workDate, String plannedStart, String plannedEnd, String reason,
                                 List<String> missing, String question) {

    public OvertimeExtraction {
        missing = missing == null ? List.of() : missing.stream().filter(Objects::nonNull).toList();
    }
}
