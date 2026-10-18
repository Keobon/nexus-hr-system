// 급여 설정(API 설계서 10.1) — 급여 항목 · 계산 변수 · 소득세 구간. 조회는 PAYROLL_READ/MANAGE, 변경 · 변수 · 구간은 PAYROLL_MANAGE
import { api } from './client';

/**
 * GET /pay-items?activeOnly → [{ id, name, itemKind EARNING|DEDUCTION, isTaxable, nonTaxableLimit, calcMethod, applyTo ALL|SELECTED,
 *   defaultAmount, baseRate, employeeRate, companyRate, baseUpperLimit, baseLowerLimit, attendanceBasis, multiplier,
 *   inOrdinaryWage, sortOrder, isActive, inUse }] 정렬 순서대로. 요율 · 배율은 퍼센트 · 소수 그대로(4.5, 1.5).
 * 4대보험은 calcMethod TAXABLE_RATE 인 공제 항목이다(적용 시작일 · 이력 없음 — 고치면 다음 정산부터).
 * inUse = 명세서 · 직원별 항목에 쓰였다 → 구분 · 계산 방식 · 적용 대상을 못 바꾸고, 삭제하면 비활성화된다
 */
export const payItemsKey = ['pay-items'];
export const getPayItems = () => api.get('/pay-items');
/** POST /pay-items — 계산 방식마다 쓰는 값만(나머지는 서버가 null 로) */
export const createPayItem = (body) => api.post('/pay-items', body);
/** PATCH /pay-items/{id} — 보낸 필드만 바꾼다 */
export const updatePayItem = (id, patch) => api.patch(`/pay-items/${id}`, patch);
/** DELETE /pay-items/{id} → { result: DELETED | DEACTIVATED } — 쓰인 적 있으면 비활성화 */
export const deletePayItem = (id) => api.delete(`/pay-items/${id}`);

/**
 * GET /pay-variables → [{ varCode, current: Row|null, upcoming: Row[], history: Row[] }]
 * Row = { value, effectiveFrom, createdAt, superseded } — 같은 적용 시작일이 여럿이면 나중 것이 정정본(앞 것 superseded)
 * varCode: DEPENDENT_DEDUCTION · MULTI_CHILD_DEDUCTION · LOCAL_TAX_RATE · ANNUAL_SPLIT_MONTHS · MONTHLY_STANDARD_HOURS
 */
export const payVariablesKey = ['pay-variables'];
export const getPayVariables = () => api.get('/pay-variables');
/** POST /pay-variables { varCode, value, effectiveFrom } — 값을 바꾸는 게 아니라 적용 시작일이 있는 새 행 */
export const createPayVariable = (body) => api.post('/pay-variables', body);

/** GET /tax-brackets → { brackets: [{ lowerBound, upperBound(마지막만 null), rate, progressiveDeduction }] } — 구간은 [이상, 미만) */
export const taxBracketsKey = ['tax-brackets'];
export const getTaxBrackets = () => api.get('/tax-brackets');
/** PUT /tax-brackets — 전체 교체. 0원부터 빈틈 · 겹침 없이, 마지막 구간만 upperBound null(아니면 TAX_BRACKET_INVALID) */
export const replaceTaxBrackets = (body) => api.put('/tax-brackets', body);
