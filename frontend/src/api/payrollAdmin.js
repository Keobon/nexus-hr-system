// 인사 · 급여 관리(API 설계서 10.2 · 10.3 · 10.4) — 정산 · 명세서 · 직원 급여 · 인건비. 조회 PAYROLL_READ, 변경 PAYROLL_MANAGE
import { api } from './client';

/** GET /payroll-runs → [{ id, payMonth "2026-09", payDate, status CONFIRMED|PAID, confirmedAt, confirmedByName, paidAt, paidByName, totals }] 최근 월부터 */
export const payrollRunsKey = ['payroll-runs'];
export const getPayrollRuns = () => api.get('/payroll-runs');
/** GET /payroll-runs/{id} → 위 + paystubs: [{ paystubId, employeeId, employeeNo, name, orgUnitName, grossPay, totalDeduction, netPay, companyBurdenTotal }] */
export const payrollRunKey = (id) => ['payroll-runs', id];
export const getPayrollRun = (id) => api.get(`/payroll-runs/${id}`);
/**
 * POST /payroll-runs/preview { payMonth, payDate?, manualInputs: [{ employeeId, payItemId, amount }] } — 저장 안 함.
 * → { payMonth, payDate, warning(PAY_MONTH_NOT_ENDED), employees: [EmployeePayroll], unregistered, negativeNetPayEmployeeIds, totals }
 */
export const previewPayroll = (body) => api.post('/payroll-runs/preview', body);
/** POST /payroll-runs — 확정(미리보기와 같은 입력, 귀속 월이 끝난 뒤에만) → PayrollRunView */
export const confirmPayroll = (body) => api.post('/payroll-runs', body);
/** POST /payroll-runs/{id}/paid — 지급완료 */
export const markPayrollPaid = (id) => api.post(`/payroll-runs/${id}/paid`);
/** POST /payroll-runs/{id}/cancel { reason } — 확정(지급완료 전)만. 그 달이 미정산으로 돌아가 다시 정산한다 */
export const cancelPayroll = (id, reason) => api.post(`/payroll-runs/${id}/cancel`, { reason });
/** GET /payroll-runs?includeCancelled=true — 취소된 정산까지(행에 cancelledAt · cancelledByName · cancelReason) */
export const payrollRunHistoryKey = ['payroll-runs', 'with-cancelled'];
export const getPayrollRunHistory = () => api.get('/payroll-runs', { includeCancelled: true });

/** GET /payslips?payMonth&orgUnitId&page&size → 페이지 [{ id, payMonth, payDate, employeeId, employeeNo, name, orgUnitName(정산 당시), grossPay, totalDeduction, netPay, companyBurdenTotal }] */
export const payslipsKey = (params) => ['payslips', 'admin', params];
export const getPayslips = (params) => api.get('/payslips', params);
/** GET /payslips/{id} → 명세서(내 명세서와 같은 모양 + companyBurdenTotal · 줄의 companyAmount) */
export const payslipKey = (id) => ['payslips', 'admin', 'detail', id];
export const getPayslip = (id) => api.get(`/payslips/${id}`);

/**
 * GET /salaries?keyword&orgUnitId&page&size — 급여 대상 재직 · 휴직 직원.
 * 행: { employeeId, employeeNo, name, orgUnitName, registered, salaryType, annualSalary, monthlyBase, effectiveFrom, fixedItemsTotal }
 */
export const salariesKey = (params) => ['salaries', 'admin', params];
export const getSalaries = (params) => api.get('/salaries', params);
/** POST /employees/{id}/salaries { salaryType, annualSalary | monthlyBase, effectiveFrom, reason } → warning PAY_MONTH_SETTLED 가능 */
export const registerSalary = (employeeId, body) => api.post(`/employees/${employeeId}/salaries`, body);
/** POST /employees/{id}/pay-items { payItemId, amount, effectiveFrom, reason } — 고정액 항목의 직원 개별 금액(0 = 받지 않음, null = 개별 해제 → 규칙 · 기본 금액) */
export const assignPayItem = (employeeId, body) => api.post(`/employees/${employeeId}/pay-items`, body);

/** GET /statistics/labor-cost?payMonth → { payMonth, settled, rows: [{ orgUnitId, orgUnitName, monthlyBudget, laborCost, executionRate }] } 예산 있는 조직만 */
export const laborCostKey = (payMonth) => ['labor-cost', payMonth];
export const getLaborCost = (payMonth) => api.get('/statistics/labor-cost', { payMonth });
