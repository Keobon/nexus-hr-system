// 조직도(API 5장) — 로그인만 하면 본다
import { api } from './client';

/** GET /org-units/tree → { id, name, levelName, lead: { id, name }, directCount, totalCount, children[] } (퇴직자 제외 인원) */
export const orgTreeKey = ['org-units', 'tree'];
export const getOrgTree = () => api.get('/org-units/tree');

/** GET /org-units/{id}/members?includeSub= → [{ id, name, jobGradeName, jobTitleName, profileFileId }] */
export const orgMembersKey = (id, includeSub = false) => ['org-units', 'members', id, includeSub];
export const getOrgMembers = (id, includeSub = false) => api.get(`/org-units/${id}/members`, { includeSub });
