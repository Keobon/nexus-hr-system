package com.nexuslabs.hr.domain.assistant.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * AI 연장근무 결재 초안(2026-11-19). 예정 시각은 "HH:mm" 그대로 — 종료가 시작보다 이르면 다음 날로 보는 건
 * 연장근무 신청(OvertimeService)이 한다. 확인하면 그 신청 ID 를 남긴다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiOvertimeDraft extends AiDraftBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate workDate;
    private String plannedStart;
    private String plannedEnd;
    private String reason;
    private Long overtimeRequestId;

    public AiOvertimeDraft(long employeeId) {
        super(employeeId);
    }

    public void fill(String userMessage, LocalDate workDate, String plannedStart, String plannedEnd, String reason,
                     OffsetDateTime expiresAt) {
        touch(userMessage, expiresAt);
        this.workDate = workDate;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.reason = reason;
    }

    @Override
    public boolean complete() {
        return workDate != null && plannedStart != null && plannedEnd != null && reason != null;
    }

    public void confirm(long overtimeRequestId) {
        markConfirmed();
        this.overtimeRequestId = overtimeRequestId;
    }
}
