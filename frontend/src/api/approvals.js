// 승인함(API 9장, F-APPR-01 · 02) — 내가 현재 단계 승인자인 건 · 처리 내역 · 승인 · 반려
// 승인자는 신청할 때 승인선(회사 설정 › 승인선)으로 계산된다: 소속 조직장 → 비었거나 본인이면 위 조직장, 최상위면 자동 승인
import { api } from './client';

/**
 * GET /approvals/inbox?workType= → 페이지 { content: [{ stepId, workType LEAVE|LEAVE_CANCEL|OVERTIME|BUSINESS_TRIP|TRIP_EXPENSE, targetId,
 *   applicantId, applicantName, applicantOrgUnitName, title, details, requestedMinutes, stepOrder, totalSteps, previousSteps }] }
 * details — 휴가: leaveTypeName · startDate · endDate · days · reason · remaining(취소면 cancelReason) / 연장: workDate · plannedStart · plannedEnd · reason
 *   / 출장: tripType · destination · purpose · startDate · endDate / 경비: destination · startDate · endDate · totalAmount · lineCount
 */
export const inboxKey = ['approvals', 'inbox'];
export const getInboxPage = (params) => api.get('/approvals/inbox', params);
/** GET /approvals/history → 페이지 { content: [{ stepId, workType, applicantName, title, status APPROVED|REJECTED, approvedMinutes, comment, actedAt }] } */
export const historyKey = ['approvals', 'history'];
export const getHistoryPage = (params) => api.get('/approvals/history', params);
/** POST /approvals/{stepId}/approve { comment?, approvedMinutes?(연장근무만, 앞 단계 값 이하) } */
export const approveStep = (stepId, body) => api.post(`/approvals/${stepId}/approve`, body ?? {});
/** POST /approvals/{stepId}/reject { comment(필수) } */
export const rejectStep = (stepId, comment) => api.post(`/approvals/${stepId}/reject`, { comment });
