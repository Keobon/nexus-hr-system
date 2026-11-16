// 결재 알림(B1, 2026-11-16) — 결재 단계가 내 차례가 되면 알림이 생긴다
import { api } from './client';

/**
 * GET /me/notifications?unreadOnly=&page=&size= → 페이지 { content: [NotificationItem] } — 안 읽은 것 먼저, 최신 먼저
 * NotificationItem: { id, type APPROVAL_REQUESTED, workType, targetId, approvalStepId, title, body, linkPath, read, readAt, createdAt }
 */
export const notificationsKey = ['notifications'];
export const getNotifications = (params) => api.get('/me/notifications', params);

/** POST /me/notifications/{id}/read → NotificationItem (멱등, 남의 알림이면 404) */
export const readNotification = (id) => api.post(`/me/notifications/${id}/read`);

/**
 * GET /api/me/notifications/stream — SSE. 기본 EventSource 는 Authorization 헤더를 못 보내서
 * useNotificationStream 이 fetch 기반 클라이언트로 직접 연다.
 * 이벤트: unread-count { unreadCount } (연결 직후 한 번) · notification (NotificationItem) · 25초마다 주석 줄(:ping)
 */
export const NOTIFICATION_STREAM_URL = '/api/me/notifications/stream';
