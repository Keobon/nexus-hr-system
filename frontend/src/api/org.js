// 조직도(API 5장) — 로그인만 하면 본다
import { api } from './client';

/** GET /org-units/tree → { id, name, levelName, lead: { id, name }, directCount, totalCount, children[] } (퇴직자 제외 인원) */
export const orgTreeKey = ['org-units', 'tree'];
export const getOrgTree = () => api.get('/org-units/tree');

/**
 * GET /org-units/{id}/members?includeSub= → [{ id, name, jobGradeName, jobTitleName, profileFileId,
 *   orgUnitId, orgUnitName, email, workStatus(OFFICE · REMOTE · FIELD · BUSINESS_TRIP · ON_VACATION · null) }] (R-115 · 116, PR #74)
 */
export const orgMembersKey = (id, includeSub = false) => ['org-units', 'members', id, includeSub];
export const getOrgMembers = (id, includeSub = false) => api.get(`/org-units/${id}/members`, { includeSub });

// 조직 관리(F-ORG-01) — ORG_MANAGE
/** 설정 화면은 비활성 조직도 본다. 응답의 monthlyBudget 은 ORG_MANAGE 가 있을 때만 */
export const orgTreeAllKey = ['org-units', 'tree', 'all'];
export const getOrgTreeAll = () => api.get('/org-units/tree', { includeInactive: true });

/** POST /org-units { parentId, name, levelName, monthlyBudget, sortOrder } */
export const createOrgUnit = (body) => api.post('/org-units', body);

/** PATCH /org-units/{id} — 보낸 필드만(name · levelName · monthlyBudget · sortOrder · leadEmployeeId). null 을 보내면 비운다 */
export const updateOrgUnit = (id, body) => api.patch(`/org-units/${id}`, body);

/** POST /org-units/{id}/move { parentId } — 하위 조직 아래로는 ORG_CYCLE */
export const moveOrgUnit = (id, parentId) => api.post(`/org-units/${id}/move`, { parentId });

/** 소속 직원 · 활성 하위 조직이 있으면 ORG_HAS_MEMBERS */
export const deactivateOrgUnit = (id) => api.post(`/org-units/${id}/deactivate`);
export const activateOrgUnit = (id) => api.post(`/org-units/${id}/activate`);

/** 트리를 펼친 목록 — [{ ...node, depth, parent(펼친 상위 항목), path: ['개발본부', '백엔드팀'] }] (최상위 회사는 depth 0) */
export function flattenOrgTree(root) {
  const out = [];
  const walk = (node, depth, parent) => {
    const entry = { ...node, depth, parent, path: depth === 0 ? [] : [...(parent?.path ?? []), node.name] };
    out.push(entry);
    [...(node.children ?? [])].sort((a, b) => a.sortOrder - b.sortOrder).forEach((c) => walk(c, depth + 1, entry));
  };
  if (root) walk(root, 0, null);
  return out;
}
