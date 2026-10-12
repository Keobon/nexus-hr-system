// 휴가(API 8장)
import { api } from './client';

/** GET /leave-types?activeOnly=true → [{ id, name, annualDays, deductsBalance, paid, sortOrder, … }] (로그인이면 조회) */
export const leaveTypesKey = ['leave-types', 'active'];
export const getLeaveTypes = () => api.get('/leave-types', { activeOnly: true });

/** GET /me/leave-balances?leaveYear= → { leaveYear, startDate, endDate, balances: [{ leaveTypeId, leaveTypeName, granted, used, pending, remaining }] } */
export const myLeaveBalancesKey = (year) => ['leave-balances', 'me', year ?? 'current'];
export const getMyLeaveBalances = (year) => api.get('/me/leave-balances', { leaveYear: year });

/** GET /me/leave-requests — 페이징. 행: { id, leaveTypeName, startDate, endDate, days, status, reason, currentStep, … } */
export const myLeaveRequestsKey = ['leave-requests', 'me'];
export const getMyLeaveRequests = () => api.get('/me/leave-requests', { size: 100, sort: 'startDate,desc' });

/** GET /leave-requests/{id} — 상세 + approvalSteps */
export const leaveRequestKey = (id) => ['leave-request', id];
export const getLeaveRequest = (id) => api.get(`/leave-requests/${id}`);

/** GET /me/leave-requests/preview → { days, leaveYear, balance, approvalLine, steps[], errors[] } */
export const previewLeave = (params) => api.get('/me/leave-requests/preview', params);

/** POST /me/leave-requests { leaveTypeId, startDate, endDate, reason } */
export const requestLeave = (body) => api.post('/me/leave-requests', body);

/** 승인대기 → 철회 */
export const withdrawLeave = (id) => api.post(`/me/leave-requests/${id}/withdraw`);

/** 승인완료 + 시작일 전 → 취소 요청 { reason } */
export const cancelLeave = (id, reason) => api.post(`/me/leave-requests/${id}/cancel-request`, { reason });

/** GET /me/team-leaves?from=&to= — 같은 조직(하위 제외) 재직 · 휴직자의 승인대기 · 승인완료 · 취소요청 휴가, 나 포함. 권한 없이 누구나(노션 2.3 L-91) */
export const teamLeavesKey = (from, to) => ['leave-requests', 'team', from, to];
export const getTeamLeaves = (from, to) => api.get('/me/team-leaves', { from, to }); // [{ employeeId, employeeName, leaveTypeName, startDate, endDate, status }]
