package com.nexuslabs.hr.domain.assistant.service;

import java.util.List;
import java.util.Objects;

/**
 * 출장 문장에서 뽑은 값 — { destination, purpose, startDate, endDate, tripType, estimatedCost, missing[], question }.
 * tripType 은 DOMESTIC · OVERSEAS, estimatedCost 는 원 단위(말했을 때만). null 은 "이번 문장으로는 모름(지금 초안 값 유지)".
 */
public record TripExtraction(String destination, String purpose, String startDate, String endDate, String tripType,
                             Long estimatedCost, List<String> missing, String question) {

    public TripExtraction {
        missing = missing == null ? List.of() : missing.stream().filter(Objects::nonNull).toList();
    }
}
