// 승인선(API 9장) — APPROVAL_MANAGE
import { api } from './client';

/**
 * GET /approval-lines?workType → [{ id, name, workType, condJobTitleId, condJobTitleName, condRoleId, condRoleName,
 *   priority, isDefault, isActive, steps: [{ stepOrder, approverType, upLevels, jobTitleId, jobTitleName, employeeId, employeeName }] }]
 * 우선순위 순, 기본 승인선은 맨 아래
 */
export const approvalLinesKey = (workType) => ['approval-lines', workType];
export const getApprovalLines = (workType) => api.get('/approval-lines', { workType });
/** { name, workType, condJobTitleId?, condRoleId?, priority, isActive, steps[] } — PUT 은 단계를 통째로 바꾼다 */
export const createApprovalLine = (body) => api.post('/approval-lines', body);
export const updateApprovalLine = (id, body) => api.put(`/approval-lines/${id}`, body);
/** → { result: DELETED | DEACTIVATED }. 기본 승인선은 APPROVAL_DEFAULT_LOCKED */
export const deleteApprovalLine = (id) => api.delete(`/approval-lines/${id}`);

/**
 * GET /approvals/reassign-needed?page&size → PageResponse<{ stepId, workType, targetId, applicantId, applicantName, title,
 *   stepOrder, status, approverId, approverName }> 오래된 순. 배열이 아니라 content 를 읽는다
 */
export const reassignKey = (params) => ['approvals', 'reassign-needed', params];
export const getReassignNeeded = (params) => api.get('/approvals/reassign-needed', params);
/** PATCH /approvals/{stepId}/approver { approverId } — 재직 · 활성 직원만, 신청자 본인 불가 */
export const reassignApprover = (stepId, approverId) => api.patch(`/approvals/${stepId}/approver`, { approverId });
