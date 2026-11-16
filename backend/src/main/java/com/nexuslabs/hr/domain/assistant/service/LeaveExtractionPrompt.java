package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.domain.leave.entity.HalfDay;

import java.time.LocalDate;
import java.util.List;

/**
 * LLM 에 보내는 것 — 오늘 날짜, 회사의 활성 휴가 종류 이름, 직원 문장, 고치는 중이면 지금 초안 값.
 * 직원 이름 · 사번 · 잔여 일수는 담지 않는다.
 */
public record LeaveExtractionPrompt(LocalDate today, List<String> leaveTypeNames, String message, Current current) {

    /** 고치는 중인 초안의 값. 새 초안이면 null. */
    public record Current(String leaveTypeName, LocalDate startDate, LocalDate endDate, HalfDay halfDay,
                          String reason) {
    }
}
