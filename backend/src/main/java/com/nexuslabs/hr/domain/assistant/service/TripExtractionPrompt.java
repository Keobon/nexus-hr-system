package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.domain.attendance.entity.TripType;

import java.time.LocalDate;

/** LLM 에 보내는 것 — 오늘 날짜, 직원 문장, 고치는 중이면 지금 초안 값. 직원 이름 · 사번은 담지 않는다. */
public record TripExtractionPrompt(LocalDate today, String message, Current current) {

    /** 고치는 중인 초안의 값. 새 초안이면 null. */
    public record Current(TripType tripType, String destination, String purpose, LocalDate startDate, LocalDate endDate,
                          Long estimatedCost) {
    }
}
