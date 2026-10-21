// 인사 · 근태 현황(API 7장) — 오늘 근태 · 월 집계 · 연장근무 · 출장 · 출장 경비 목록. 모두 ATTENDANCE_READ 범위(팀이면 내 팀)
// 정정 대상 · 정정은 attendance.js(getAttendanceCorrections · correctAttendance)
import { api } from './client';

/** GET /attendances/today?orgUnitId= → 페이지 { content: [{ employeeId, name, orgUnitName, todayStatus, workType, placeMemo, checkInAt, checkOutAt }] } */
export const getTodayPage = (params) => api.get('/attendances/today', params);

/** GET /overtime-requests?month&orgUnitId&status → 페이지 { content: [{ id, employeeName, orgUnitName, workDate, plannedStart, plannedEnd,
 *   requestedMinutes, approvedMinutes, holiday, nightMinutes, reason, status, rejectReason }] } */
export const getOvertimePage = (params) => api.get('/overtime-requests', params);

/** GET /business-trips?from&to&orgUnitId&status — 기간이 걸치는 출장. 행 { id, employeeName, orgUnitName, tripType, destination, startDate, endDate,
 *   estimatedCost, status, reportWritten, expenseClaimStatus, rejectReason } */
export const getTripPage = (params) => api.get('/business-trips', params);
/** GET /business-trips/{id} — 목적(purpose)은 상세에만 있다 */
export const tripKey = (id) => ['trips', 'detail', id];
export const getTripDetail = (id) => api.get(`/business-trips/${id}`);

/** GET /expense-claims?status&settled&orgUnitId — 월 조건은 없다. 행 { id, businessTripId, destination, employeeName, orgUnitName, totalAmount, lineCount,
 *   status, settledPayMonth, createdAt, rejectReason } */
export const getExpensePage = (params) => api.get('/expense-claims', params);
