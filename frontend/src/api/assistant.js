// AI 휴가 결재 초안(C1, 2026-11-16) — AI 는 초안만 만들고, 직원이 [올리기]를 눌러야 기존 휴가 신청 로직으로 신청된다
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
