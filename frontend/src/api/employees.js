// 직원(API 6장)
import { api } from './client';

/**
 * GET /employees — 페이징. 행: { id, employeeNo, name, orgUnitId, orgUnitName, jobGradeName, jobTitleName,
 *   employmentTypeName, status, todayStatus, hireDate, annualSalary? } (범위는 서버가 좁힌다 — 조직장은 자기 팀만)
 * annualSalary 는 PAYROLL_READ 가 있을 때만 키가 온다. 전화 · 이메일 · 주소는 없다(연락처 API 로 채운다)
 */
export const employeesKey = (params) => ['employees', params];
export const getEmployees = (params) => api.get('/employees', params);

/**
 * GET /employees/contacts?orgUnitId&keyword&page&size — 재직 · 휴직만(퇴직자는 서버가 뺀다).
 * 전사 조회 권한이 있거나 조직장이면 전 직원, 아니면 FORBIDDEN(PR #87).
 * 행: { id, employeeNo, name, orgUnitName, jobGradeName, jobTitleName, email, phone, address }
 */
export const employeeContactsKey = (params) => ['employees', 'contacts', params];
export const getEmployeeContacts = (params) => api.get('/employees/contacts', params);

/** 페이지를 다 돌며 모은다(size=100) — "검색 결과 전체 선택"으로 보낼 때 */
export async function fetchAllPages(fetchPage, params) {
  const all = [];
  for (let page = 0; ; page += 1) {
    const res = await fetchPage({ ...params, page, size: 100 });
    all.push(...(res.content ?? []));
    if (page + 1 >= (res.totalPages ?? 1)) return all;
  }
}

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
