package com.nexuslabs.hr.domain.notification.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;

import java.time.OffsetDateTime;

/**
 * 알림 한 건 — GET /api/me/notifications 목록 행, POST …/{id}/read 응답, SSE event: notification 의 data.
 * linkPath 는 누르면 갈 화면(결재 알림은 /approvals?stepId={단계 ID}). read 는 readAt 이 있으면 true.
 */
public record NotificationItem(long id, String type, ApprovalWorkType workType, Long targetId, Long approvalStepId,
                               String title, String body, String linkPath, boolean read, OffsetDateTime readAt,
                               OffsetDateTime createdAt) {
}
