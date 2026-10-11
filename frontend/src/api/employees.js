// 직원(API 6장)
import { api } from './client';

/** GET /employees — 페이징. 행: { id, name, orgUnitName, todayStatus, … } (범위는 서버가 좁힌다) */
export const employeesKey = (params) => ['employees', params];
export const getEmployees = (params) => api.get('/employees', params);
