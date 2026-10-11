// 대시보드 홈(API 13장). 권한에 따라 섹션(company · myOrg · admin)이 붙거나 빠진다 — 없는 섹션은 그리지 않는다
import { api } from './client';

export const dashboardKey = ['dashboard', 'home'];

export const getDashboardHome = () => api.get('/dashboard/home');
