package com.nexuslabs.hr.domain.notification.service;

import com.nexuslabs.hr.domain.notification.dto.NotificationItem;

/** 알림 행을 저장했다 — 커밋된 뒤에만 SSE 로 보낸다({@link NotificationPushListener}). */
public record NotificationCreated(long companyId, long recipientId, NotificationItem item) {
}
