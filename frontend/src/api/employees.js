// 직원(API 6장)
import { api } from './client';

/** GET /employees — 페이징. 행: { id, name, orgUnitName, todayStatus, … } (범위는 서버가 좁힌다) */
export const employeesKey = (params) => ['employees', params];
export const getEmployees = (params) => api.get('/employees', params);

/** GET /employees/{id} — 팀 범위면 full 필드(hireDate · orgUnitId · dependentsCount …)가 빠진다 */
export const employeeKey = (id) => ['employees', 'detail', String(id)];
export const getEmployee = (id) => api.get(`/employees/${id}`);

/** POST /employees → { id, employeeNo, name, status, temporaryPassword, leaveGrants[] } — 임시 비밀번호는 이 응답에서 한 번만 */
export const createEmployee = (body) => api.post('/employees', body);

/** PATCH /employees/{id} — 보낸 필드만 바꾼다. 조직 · 직급 · 직책은 인사발령으로만(서버가 무시) */
export const updateEmployee = (id, patch) => api.patch(`/employees/${id}`, patch);

/** POST /employees/{id}/status { status, effectiveDate, reason } → 이력 한 줄. 퇴직이면 계정 · 조직장 등을 서버가 같이 처리 */
export const changeEmploymentStatus = (id, body) => api.post(`/employees/${id}/status`, body);

/** GET /employees/{id}/status-history → [{ id, status, effectiveDate, reason, createdByName, createdAt }] 시간순 */
export const employmentStatusHistoryKey = (id) => ['employees', 'status-history', String(id)];
export const getEmploymentStatusHistory = (id) => api.get(`/employees/${id}/status-history`);
