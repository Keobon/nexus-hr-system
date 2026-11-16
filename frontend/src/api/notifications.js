// 결재 알림(B1, 2026-11-16) — 결재 단계가 내 차례가 되면 알림이 생긴다
import { api } from './client';

/**
 * GET /me/notifications?unreadOnly=&page=&size= → 페이지 { content: [NotificationItem] } — 안 읽은 것 먼저, 최신 먼저
 * NotificationItem: { id, type APPROVAL_REQUESTED, workType, targetId, approvalStepId, title, body, linkPath, read, readAt, createdAt }
 */
export const notificationsKey = ['notifications'];
export const getNotifications = (params) => api.get('/me/notifications', params);

/** 안 읽은 알림(뱃지 숫자 = totalElements, AI 비서를 열 때 말풍선). notificationsKey 로 무효화하면 같이 새로 받는다 */
export const unreadNotificationsKey = [...notificationsKey, 'unread'];
export const getUnreadNotifications = () => getNotifications({ unreadOnly: true, size: 100 });

/** POST /me/notifications/{id}/read → NotificationItem (멱등, 남의 알림이면 404) */
export const readNotification = (id) => api.post(`/me/notifications/${id}/read`);

/**
 * 승인함에서 처리(승인 · 반려)한 단계의 안 읽은 알림을 읽음으로 — 알림을 누르지 않고 바로 처리해도 뱃지에서 빠지게.
 * 실패해도 처리 결과에는 영향 없다(다음에 AI 비서를 열 때 "이미 처리된 결재"로 정리된다).
 */
export async function readNotificationsForSteps(stepIds) {
  const ids = new Set(stepIds);
  try {
    const page = await getUnreadNotifications();
    await Promise.all(
      (page?.content ?? [])
        .filter((n) => ids.has(n.approvalStepId))
        .map((n) => readNotification(n.id).catch(() => null)),
    );
  } catch {
    // 무시
  }
}

/**
 * GET /api/me/notifications/stream — SSE. 기본 EventSource 는 Authorization 헤더를 못 보내서
 * useNotificationStream 이 fetch 기반 클라이언트로 직접 연다.
 * 이벤트: unread-count { unreadCount } (연결 직후 한 번) · notification (NotificationItem) · 25초마다 주석 줄(:ping)
 */
export const NOTIFICATION_STREAM_URL = '/api/me/notifications/stream';
