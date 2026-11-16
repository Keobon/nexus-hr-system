package com.nexuslabs.hr.domain.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 알림만 접속 중인 받는 사람에게 SSE 로 보낸다 — 롤백된 결재의 알림은 나가지 않는다.
 * 커밋을 마친 스레드에서 돌므로 TenantContext 에 기대지 않고 이벤트의 companyId 를 쓴다.
 */
@Component
public class NotificationPushListener {

    private final SseEmitterRegistry registry;

    public NotificationPushListener(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreated event) {
        registry.send(event.companyId(), event.recipientId(), SseEmitterRegistry.EVENT_NOTIFICATION, event.item());
    }
}
