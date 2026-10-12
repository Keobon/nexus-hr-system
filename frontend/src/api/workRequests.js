// 연장근무 · 출장 · 출장 경비(API 7.2–7.3) — 목록 조회는 attendance.js 의 getMyOvertimes · getMyTrips 를 같이 쓴다
import { api } from './client';

/** 쿼리 키 앞부분 — 신청 · 철회 뒤 한 번에 무효화 */
export const overtimeRoot = ['overtime'];
export const tripRoot = ['trips'];
export const expenseRoot = ['expenses'];

/** GET /overtime-requests/{id} — 상세 + approvalSteps · approvedMinutes */
export const overtimeKey = (id) => ['overtime', 'detail', id];
export const getOvertime = (id) => api.get(`/overtime-requests/${id}`);

/** POST /me/overtime-requests { workDate, plannedStart "18:00", plannedEnd, reason } — 종료가 시작보다 이르면 다음 날 */
export const requestOvertime = (body) => api.post('/me/overtime-requests', body);
export const withdrawOvertime = (id) => api.post(`/me/overtime-requests/${id}/withdraw`);

/** GET /business-trips/{id} — 상세(목적 · 결과 보고 · 경비 청구 요약 · approvalSteps) */
export const tripKey = (id) => ['trips', 'detail', id];

/** POST /me/business-trips { tripType DOMESTIC|OVERSEAS, destination, purpose, startDate, endDate, estimatedCost } */
export const requestTrip = (body) => api.post('/me/business-trips', body);
export const withdrawTrip = (id) => api.post(`/me/business-trips/${id}/withdraw`);

/** PUT /me/business-trips/{id}/report { reportText } — 승인된 출장의 결과 보고(작성 · 수정) */
export const reportTrip = (id, reportText) => api.put(`/me/business-trips/${id}/report`, { reportText });

/** GET /me/expense-claims — 행 { id, businessTripId, destination, totalAmount, lineCount, status, settledPayMonth, createdAt, currentStep } */
export const myExpensesKey = ['expenses', 'me'];
export const getMyExpenses = () => api.get('/me/expense-claims', { size: 50 });
export const expenseKey = (id) => ['expenses', 'detail', id];
export const getExpense = (id) => api.get(`/expense-claims/${id}`);

/** POST /me/expense-claims { businessTripId, lines: [{ expenseTypeId, usedDate, amount, description, receiptFileId }] } */
export const requestExpense = (body) => api.post('/me/expense-claims', body);
export const withdrawExpense = (id) => api.post(`/me/expense-claims/${id}/withdraw`);

/** GET /expense-types?activeOnly=true → [{ id, name, … }] */
export const expenseTypesKey = ['expense-types', 'active'];
export const getExpenseTypes = () => api.get('/expense-types', { activeOnly: true });
