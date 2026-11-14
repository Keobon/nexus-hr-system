// 퇴사 통계(작업 지시서 2-4). DASHBOARD_COMPANY(경영진 · 인사 담당)만 — 아니면 403. 기간은 YYYY-MM(시작 · 종료 달 포함)
// 계산식(서버): 재직 인원(D) = 입사일 <= D 이고 (퇴사일 없음 또는 퇴사일 > D) · 퇴사율 = 기간 퇴사자 ÷ 평균 재직 인원 × 100(소수 첫째 자리, 평균 0 이면 0)
// 평균 재직 인원 = (기간 시작일 재직 + 기간 종료일 재직) ÷ 2 · 순증감 = 입사자 − 퇴사자
import { api } from './client';

/** → { month, headcount, hires, resignations, turnoverRate, prevTurnoverRate, netChange } — headcount 는 그달 말일 재직 인원 */
export const turnoverSummaryKey = (month) => ['turnover', 'summary', month];
export const getTurnoverSummary = (month) => api.get('/stats/turnover/summary', { month });

/** unit = month | quarter | year → [{ period('2026-03' · '2026-Q1' · '2026'), hires, resignations, avgHeadcount, turnoverRate }] 오래된 순 */
export const turnoverTrendKey = (p) => ['turnover', 'trend', p];
export const getTurnoverTrend = (p) => api.get('/stats/turnover/trend', p);

/** → [{ orgUnitId, orgUnitName, avgHeadcount, resignations, turnoverRate }] — 최상위 바로 아래 조직(하위 포함) */
export const turnoverByDeptKey = (p) => ['turnover', 'by-department', p];
export const getTurnoverByDept = (p) => api.get('/stats/turnover/by-department', p);

/** → [{ bucket(UNDER_1Y · Y1_3 · Y3_5 · OVER_5Y), count }] — 근속 = 퇴사일 − 입사일 */
export const turnoverByTenureKey = (p) => ['turnover', 'by-tenure', p];
export const getTurnoverByTenure = (p) => api.get('/stats/turnover/by-tenure', p);

/** → [{ reason(JOB_CHANGE …), count, ratio(%) }] */
export const turnoverByReasonKey = (p) => ['turnover', 'by-reason', p];
export const getTurnoverByReason = (p) => api.get('/stats/turnover/by-reason', p);

/**
 * 퇴사자 목록 → 페이지 { content: [{ employeeId, name, orgUnitName, jobGradeName, hireDate, resignDate, tenureDays, resignReason }] }
 * EMPLOYEE_MANAGE 가 없으면(경영진) name 은 서버가 가려서 준다(예: 홍*동). 퇴사일 최근 순
 */
export const resignationsKey = (p) => ['turnover', 'resignations', p];
export const getResignations = (p) => api.get('/stats/turnover/resignations', p);

/** 근속 구간 이름 */
export const TENURE_BUCKETS = [
  ['UNDER_1Y', '1년 미만'],
  ['Y1_3', '1~3년'],
  ['Y3_5', '3~5년'],
  ['OVER_5Y', '5년 이상'],
];
