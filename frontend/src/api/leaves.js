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

/** 인사 · 휴가 현황(API 8장) — LEAVE_READ 범위(팀이면 내 팀)
 * GET /leave-requests?from&to&orgUnitId&status&leaveTypeId — 기간이 걸치는 휴가. 행 { id, employeeId, employeeName, orgUnitName, leaveTypeName,
 *   startDate, endDate, days, status, reason, currentStep: { stepOrder, totalSteps, approverName, status } | null, rejectReason } */
export const getLeaveRequestPage = (params) => api.get('/leave-requests', params);
/** GET /leave-types — 사용 중지 포함(목록 필터용) */
export const allLeaveTypesKey = ['leave-types', 'all'];
export const getAllLeaveTypes = () => api.get('/leave-types');
/** GET /leave-balances?leaveYear&orgUnitId → 페이지 { content: [{ employeeId, employeeNo, name, orgUnitName, balances: [{ leaveTypeId, leaveTypeName, granted, used, pending, remaining }] }] } */
export const getLeaveBalancePage = (params) => api.get('/leave-balances', params);

/** 휴가 부여(LEAVE_MANAGE) — POST /leave-grants/annual { leaveYear } → { leaveYear, created, skipped } */
export const grantAnnualLeave = (leaveYear) => api.post('/leave-grants/annual', { leaveYear });
/** POST /leave-grants/adjustments { employeeId, leaveTypeId, leaveYear, days(±, 0 불가), reason } — 잔여가 음수가 되면 거절 */
export const adjustLeaveGrant = (body) => api.post('/leave-grants/adjustments', body);
/** GET /leave-grants?employeeId&leaveYear → [{ id, leaveTypeName, leaveYear, grantType REGULAR|HIRE|ADJUSTMENT, days, reason, createdByName, createdAt }] */
export const leaveGrantsKey = (employeeId, leaveYear) => ['leave-grants', employeeId, leaveYear];
export const getLeaveGrants = (employeeId, leaveYear) => api.get('/leave-grants', { employeeId, leaveYear });
