package com.nexuslabs.hr.domain.assistant.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * POST /api/me/ai/overtime-drafts 응답 — 휴가 초안 응답과 같은 형태 { draftId, status, question, draft, preview, errors }.
 * question 은 서버가 만든 문장이다(AI 가 만든 글을 그대로 보여 주지 않는다). NEED_INFO 면 preview 는 null.
 */
public record AiOvertimeDraftResponse(long draftId, AiDraftResult status, String question, Draft draft,
                                      Preview preview, List<String> errors) {

    /** 지금까지 모인 값. 모르는 칸은 null. 예정 시각은 "HH:mm" — 종료가 시작보다 이르면 다음 날. */
    public record Draft(LocalDate workDate, String plannedStart, String plannedEnd, String reason,
                        OffsetDateTime expiresAt) {
    }

    /** 연장근무 신청과 같은 검사 결과. minutes 는 총 분(다음 날로 넘어가면 그만큼), errors 는 기존 오류 코드. */
    public record Preview(int minutes, List<DraftApprovalStep> approvalSteps, List<String> errors) {
    }
}
