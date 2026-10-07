package com.nexuslabs.hr.domain.approval.service;

import java.time.OffsetDateTime;

/**
 * 승인 단계 한 줄 — 신청 상세의 approvalSteps(API 설계서 9장)와 같은 형식.
 * isMyTurn 은 조회한 사람이 지금 처리할 차례인지.
 */
public record ApprovalStepView(long stepId, int round, int stepOrder, Long approverId, String approverName,
                               ApprovalStepStatus status, Integer approvedMinutes, String comment,
                               OffsetDateTime actedAt, boolean isMyTurn) {
}
