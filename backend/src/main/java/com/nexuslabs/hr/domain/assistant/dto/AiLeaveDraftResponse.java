package com.nexuslabs.hr.domain.assistant.dto;

import com.nexuslabs.hr.domain.leave.dto.LeavePreview;
import com.nexuslabs.hr.domain.leave.entity.HalfDay;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * POST /api/me/ai/leave-drafts 응답.
 * <ul>
 *   <li>READY — 확인 카드를 보여 줄 수 있다. preview 는 기존 휴가 미리보기와 같다.</li>
 *   <li>NEED_INFO — question 으로 되묻는다. 답은 draftId 와 함께 다시 보낸다. preview 는 null.</li>
 *   <li>INVALID — 값은 다 모였지만 이대로는 신청할 수 없다(잔여 부족 등). errors 는 기존 휴가 오류 코드.</li>
 * </ul>
 * question 은 서버가 만든 문장이다(AI 가 만든 글을 그대로 보여 주지 않는다).
 */
public record AiLeaveDraftResponse(long draftId, Status status, String question, Draft draft, LeavePreview preview,
                                   List<String> errors) {

    public enum Status {
        READY, NEED_INFO, INVALID
    }

    /** 지금까지 모인 값. 모르는 칸은 null. halfDay 가 null 이면 종일. */
    public record Draft(Long leaveTypeId, String leaveTypeName, LocalDate startDate, LocalDate endDate,
                        HalfDay halfDay, String reason, OffsetDateTime expiresAt) {
    }
}
