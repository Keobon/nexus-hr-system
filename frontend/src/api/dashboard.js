// 대시보드 홈(API 13장). 권한에 따라 섹션(company · myOrg · admin)이 붙거나 빠진다 — 없는 섹션은 그리지 않는다
import { api } from './client';

export const dashboardKey = ['dashboard', 'home'];

export const getDashboardHome = () => api.get('/dashboard/home');

// 홈 company.performance(F-DASH-06) · company.laborCost(F-DASH-07) — 다른 평가 기간 · 정산 월을 고를 때. 권한이 없으면 홈에 키가 없다
/** DASHBOARD_COMPANY + EVAL_READ(전사) → performance 와 같은 모양 + availableCycles [{ evalCycleId, cycleName, cycleKind, closedAt }] */
export const companyPerformanceKey = (evalCycleId) => ['dashboard', 'performance', evalCycleId ?? null];
export const getCompanyPerformance = (evalCycleId) => api.get('/dashboard/company/performance', evalCycleId ? { evalCycleId } : undefined);

/** DASHBOARD_COMPANY + PAYROLL_READ → { payMonth, headcount, total, changeRate, byOrgUnit, employees(페이지), availableMonths } */
export const companyLaborCostKey = (params) => ['dashboard', 'labor-cost', params];
export const getCompanyLaborCost = (params) => api.get('/dashboard/company/labor-cost', params);
