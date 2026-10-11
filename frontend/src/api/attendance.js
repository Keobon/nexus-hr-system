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
