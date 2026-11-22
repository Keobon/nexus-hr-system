package com.nexuslabs.hr.domain.assistant.dto;

import com.nexuslabs.hr.domain.attendance.entity.TripType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * POST /api/me/ai/trip-drafts 응답 — 휴가 초안 응답과 같은 형태 { draftId, status, question, draft, preview, errors }.
 * question 은 서버가 만든 문장이다. NEED_INFO 면 preview 는 null.
 */
public record AiTripDraftResponse(long draftId, AiDraftResult status, String question, Draft draft, Preview preview,
                                  List<String> errors) {

    /** 지금까지 모인 값. 모르는 칸은 null. estimatedCost 는 말했을 때만. */
    public record Draft(TripType tripType, String destination, String purpose, LocalDate startDate, LocalDate endDate,
                        Long estimatedCost, OffsetDateTime expiresAt) {
    }

    /** 출장 신청과 같은 검사 결과. days 는 출장 일수(시작 · 종료일 포함), errors 는 기존 오류 코드. */
    public record Preview(int days, List<DraftApprovalStep> approvalSteps, List<String> errors) {
    }
}
