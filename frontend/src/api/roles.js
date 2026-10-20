// 역할 · 권한 · 계정(API 4장) — ROLE_MANAGE
import { api } from './client';

/** GET /permissions → [{ code, description, allowedScopes ['TEAM','ALL'] | ['ALL'], relatedFeatures }] 18개 */
export const permissionsKey = ['permissions'];
export const getPermissions = () => api.get('/permissions');

/** GET /roles → [{ id, name, description, isSystem, accountCount }] 시스템 역할 먼저 */
export const rolesKey = ['roles'];
export const getRoles = () => api.get('/roles');
/** GET /roles/{id} → { …, permissions: [{ code, scope }] } */
export const roleKey = (id) => ['roles', String(id)];
export const getRole = (id) => api.get(`/roles/${id}`);
/** { name, description, permissions: [{ code, scope }] } */
export const createRole = (body) => api.post('/roles', body);
/** PUT — 권한 목록을 통째로 바꾼다. 시스템 역할은 SYSTEM_ROLE_READONLY */
export const updateRole = (id, body) => api.put(`/roles/${id}`, body);
/** 계정이 있거나 승인선 조건에 쓰이면 ITEM_IN_USE */
export const deleteRole = (id) => api.delete(`/roles/${id}`);

/**
 * GET /accounts?roleId&active&locked&keyword&page&size → PageResponse<{ employeeId, employeeNo, name, email, orgUnitName,
 *   roleId, roleName, active, locked, lockedUntil, lastLoginAt }>
 */
export const accountsKey = (params) => ['accounts', params];
export const getAccounts = (params) => api.get('/accounts', params);
/** PATCH /accounts/{employeeId} { roleId } 또는 { active } — 바로 반영. 마지막 최고 관리자는 LAST_SUPER_ADMIN */
export const updateAccount = (employeeId, patch) => api.patch(`/accounts/${employeeId}`, patch);
export const unlockAccount = (employeeId) => api.post(`/accounts/${employeeId}/unlock`);
/** → { temporaryPassword } — 한 번만 보여 준다 */
export const resetPassword = (employeeId) => api.post(`/accounts/${employeeId}/reset-password`);
