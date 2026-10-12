// 내 급여(API 10장) — 명세서 · 급여 이력
import { api } from './client';

/** GET /me/payslips → [{ id, payMonth "2026-09", payDate, status CONFIRMED|PAID, netPay }] 최근 귀속 월부터 */
export const myPayslipsKey = ['payslips', 'me'];
export const getMyPayslips = () => api.get('/me/payslips');

/**
 * GET /me/payslips/{id} — 명세서(F-PAY-06). 정산 당시 저장된 값. 회사부담 필드 없음.
 * { payMonth, payDate, status, company, employeeNo, name, orgUnitName, bankAccount, basePay, ordinaryHourlyWage,
 *   workedDays, monthDays, dependentsCount, childrenCount, lines: [{ payItemId, name, itemKind, calcMethod, amount,
 *   taxableAmount, nonTaxableAmount, quantity, unitPrice, formulaNote }], grossPay, taxablePay, incomeTax,
 *   localIncomeTax, totalDeduction, netPay, expenseClaimIds }
 */
export const myPayslipKey = (id) => ['payslips', 'me', id];
export const getMyPayslip = (id) => api.get(`/me/payslips/${id}`);

/** GET /me/salaries → { payrollEligible, current, salaries: [{ effectiveFrom, salaryType, annualSalary, monthlyBase, reason }], payItems } */
export const mySalariesKey = ['salaries', 'me'];
export const getMySalaries = () => api.get('/me/salaries');
