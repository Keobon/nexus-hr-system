// 근무시간 · 휴일(API 3장) — 조회는 로그인, 변경은 COMPANY_MANAGE
import { api } from './client';

/**
 * GET /work-schedules → { current, upcoming[], history[] }
 * 행 = { id, effectiveFrom, startTime 'HH:mm', endTime, breakMinutes, lateGraceMinutes, nightStart, nightEnd,
 *        overtimeApprovalRequired, workDays ['MON',…], dailyStandardMinutes }
 */
export const workSchedulesKey = ['work-schedules'];
export const getWorkSchedules = () => api.get('/work-schedules');
/** POST /work-schedules — 수정은 없고 새 적용 시작일로 한 줄 더(오늘 또는 미래) */
export const createWorkSchedule = (body) => api.post('/work-schedules', body);
/** DELETE /work-schedules/{id} — 적용 예정(미래) 행만 */
export const deleteWorkSchedule = (id) => api.delete(`/work-schedules/${id}`);

/** GET /holidays?year → [{ id, holidayDate, name, holidayType, isRecurring }] — 매년 반복은 그 해 날짜로 펼쳐서 온다 */
export const holidaysKey = (year) => ['holidays', year];
export const getHolidays = (year) => api.get('/holidays', { year });
/** { holidayDate, name, holidayType PUBLIC|COMPANY|SUBSTITUTE, isRecurring } */
export const createHoliday = (body) => api.post('/holidays', body);
export const updateHoliday = (id, patch) => api.patch(`/holidays/${id}`, patch);
export const deleteHoliday = (id) => api.delete(`/holidays/${id}`);

export const WEEKDAYS = [
  ['MON', '월'], ['TUE', '화'], ['WED', '수'], ['THU', '목'], ['FRI', '금'], ['SAT', '토'], ['SUN', '일'],
];
