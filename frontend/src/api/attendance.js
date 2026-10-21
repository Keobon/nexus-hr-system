// 출퇴근 · 근태(API 7.1)
import { api } from './client';

export const todayKey = ['attendance', 'today'];

/** → { workDate, isWorkday, status, workType, placeMemo, checkInAt, checkOutAt } */
export const getToday = () => api.get('/me/attendance/today');

/** body: { workType: 'OFFICE' | 'REMOTE' | 'FIELD', placeMemo?, lat?, lng? } */
export const checkIn = (body) => api.post('/me/attendance/check-in', body);

/** body: { lat?, lng? } */
export const checkOut = (body) => api.post('/me/attendance/check-out', body);

/** 퇴근 전 근무 형태 변경 — body: { workType, placeMemo } */
export const changeWorkType = (body) => api.patch('/me/attendance/today', body);

/** GET /me/attendances?month=YYYY-MM → { month, days[], summary } — 날짜별 행(기록 없는 근무일 포함) + 월 합계 */
export const myAttendancesKey = (month) => ['attendance', 'me', month];
export const getMyAttendances = (month) => api.get('/me/attendances', { month });

/** GET /attendances/employees/{id}?month= — 한 직원의 날짜별 행(ATTENDANCE_READ 범위 안) */
export const employeeAttendancesKey = (id, month) => ['attendance', 'employee', id, month];
export const getEmployeeAttendances = (id, month) => api.get(`/attendances/employees/${id}`, { month });

/** GET /work-schedules → { current: { startTime, endTime, breakMinutes, dailyStandardMinutes, … }, upcoming, history } */
export const workSchedulesKey = ['work-schedules'];
export const getWorkSchedules = () => api.get('/work-schedules');

/** GET /me/overtime-requests?month=&status= — 페이징, 근무일 최신순 */
export const myOvertimesKey = (month) => ['overtime', 'me', month];
export const getMyOvertimes = (month) => api.get('/me/overtime-requests', { month, size: 100 });

/** GET /me/business-trips — 페이징. 행에 목적(purpose)은 없고 상세에 있다 */
export const myTripsKey = ['trips', 'me'];
export const getMyTrips = () => api.get('/me/business-trips', { size: 50 });
export const getTrip = (id) => api.get(`/business-trips/${id}`);

/** GET /attendances/today?orgUnitId= — 직원들의 오늘 근태(ATTENDANCE_READ 범위, 하위 조직 포함, 퇴직자 제외)
 * 행 { employeeId, name, orgUnitName, todayStatus, workType, placeMemo, checkInAt, checkOutAt } — 페이징 */
export const teamTodayKey = (orgUnitId) => ['attendance', 'team-today', orgUnitId];
export const getTeamToday = (orgUnitId) => api.get('/attendances/today', { orgUnitId, size: 50 });

/** GET /attendances?month=&orgUnitId= — 직원별 월 합계(ATTENDANCE_READ). 행 { employeeId, employeeNo, name, orgUnitName, summary } — 페이징 */
export const attendanceMonthKey = (month, orgUnitId) => ['attendance', 'month', month, orgUnitId ?? null];
export const getAttendanceMonth = (params) => api.get('/attendances', params);

/**
 * 근태 정정(F-ATT-04, ATTENDANCE_MANAGE)
 * GET /attendances/corrections → [{ type MISSING_CHECKOUT | LEAVE_CONFLICT | TRIP_CONFLICT, attendanceId, employeeId, employeeNo, name, orgUnitName,
 *   workDate, status, workType, checkInAt, checkOutAt, conflict: { id, name, startDate, endDate } | null }]
 */
export const attendanceCorrectionsKey = ['attendance', 'corrections'];
export const getAttendanceCorrections = () => api.get('/attendances/corrections');
/** PATCH /attendances/{id} { status?, checkInAt?, checkOutAt?, workType?, reason } — 보낸 필드만 바꾼다. 값 없이 reason 만 보내면 "확인" */
export const correctAttendance = (id, patch) => api.patch(`/attendances/${id}`, patch);
/** POST /attendances { employeeId, workDate, status, checkInAt?, checkOutAt?, workType?, reason } — 기록이 없는 날에 만든다 */
export const createAttendance = (body) => api.post('/attendances', body);
