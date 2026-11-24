// AI 결재 초안 — 휴가(C1, 2026-11-16) · 연장근무 · 출장(#161, 2026-11-22). AI 는 초안만 만들고, 직원이 [올리기]를 눌러야 기존 신청 로직으로 신청된다
// 세 업무 모두 응답 모양이 같다: { draftId, status READY|NEED_INFO|INVALID, question, draft, preview, errors } — 다른 것은 draft · preview 칸뿐
// 오류(공통): AI_UNAVAILABLE(503) · AI_RATE_LIMITED(429) · AI_DRAFT_EXPIRED · AI_DRAFT_NOT_FOUND
import { api } from './client';

/**
 * POST /me/ai/leave-drafts { message, draftId? } → { draftId, status READY|NEED_INFO|INVALID, question, draft, preview, errors }
 * draft: { leaveTypeId, leaveTypeName, startDate, endDate, halfDay AM|PM|null, reason, expiresAt }
 * preview: 휴가 미리보기와 같은 모양 { days, leaveYear, balance: { remaining, remainingAfter, … }, approvalLine, steps[], errors[] } (NEED_INFO 면 null)
 * 오류: AI_UNAVAILABLE(503) · AI_RATE_LIMITED(429) · AI_DRAFT_EXPIRED · AI_DRAFT_NOT_FOUND
 */
export const createLeaveDraft = (body) => api.post('/me/ai/leave-drafts', body);

/** POST /me/ai/leave-drafts/{id}/confirm → 만들어진 휴가 신청(GET /leave-requests/{id} 와 같다) */
export const confirmLeaveDraft = (id) => api.post(`/me/ai/leave-drafts/${id}/confirm`);

/** POST /me/ai/leave-drafts/{id}/discard → { draftId, status: DISCARDED } */
export const discardLeaveDraft = (id) => api.post(`/me/ai/leave-drafts/${id}/discard`);

/**
 * POST /me/ai/route { message } → { workType: LEAVE | OVERTIME | BUSINESS_TRIP | UNKNOWN }
 * 칩을 고르지 않고 바로 쓴 문장이 어떤 결재인지만 고른다(초안은 만들지 않는다) — 그 업무의 초안 API 로 같은 문장을 이어서 보낸다
 */
export const routeMessage = (body) => api.post('/me/ai/route', body);

/**
 * POST /me/ai/overtime-drafts { message, draftId? } → 위 공통 모양
 * draft: { workDate, plannedStart "HH:mm", plannedEnd "HH:mm"(시작보다 이르거나 같으면 다음 날), reason, expiresAt }
 * preview: { minutes(총 분), approvalSteps[{ stepOrder, approverId, approverName, skipped, stepLabel }], errors[] } (NEED_INFO 면 null)
 */
export const createOvertimeDraft = (body) => api.post('/me/ai/overtime-drafts', body);

/** POST /me/ai/overtime-drafts/{id}/confirm → 만들어진 연장근무 신청 */
export const confirmOvertimeDraft = (id) => api.post(`/me/ai/overtime-drafts/${id}/confirm`);

/** POST /me/ai/overtime-drafts/{id}/discard → { draftId, status: DISCARDED } */
export const discardOvertimeDraft = (id) => api.post(`/me/ai/overtime-drafts/${id}/discard`);

/**
 * POST /me/ai/trip-drafts { message, draftId? } → 위 공통 모양
 * draft: { tripType DOMESTIC|OVERSEAS, destination, purpose, startDate, endDate, estimatedCost(말했을 때만), expiresAt }
 * preview: { days(시작 · 종료일 포함), approvalSteps[{ stepOrder, approverId, approverName, skipped, stepLabel }], errors[] } (NEED_INFO 면 null)
 */
export const createTripDraft = (body) => api.post('/me/ai/trip-drafts', body);

/** POST /me/ai/trip-drafts/{id}/confirm → 만들어진 출장 신청 */
export const confirmTripDraft = (id) => api.post(`/me/ai/trip-drafts/${id}/confirm`);

/** POST /me/ai/trip-drafts/{id}/discard → { draftId, status: DISCARDED } */
export const discardTripDraft = (id) => api.post(`/me/ai/trip-drafts/${id}/discard`);
