// 직급 · 직책 · 고용형태(API 5장) — 목록은 로그인만 하면, 등록 · 수정 · 삭제는 ORG_MANAGE
import { api } from './client';

const settingApi = (base, keyName) => ({
  key: (activeOnly = false) => [keyName, { activeOnly }],
  /** → [{ id, name, sortOrder, isActive }] — 등록 · 발령 화면의 선택지는 activeOnly=true */
  list: (activeOnly = false) => api.get(base, { activeOnly }),
  /** { name, sortOrder, isActive } */
  create: (body) => api.post(base, body),
  /** 수정도 name · sortOrder 를 모두 보낸다. isActive 로 비활성화 · 다시 활성화 */
  update: (id, body) => api.patch(`${base}/${id}`, body),
  /** 쓰인 적 없으면 지우고 DELETED, 쓰였으면 비활성화하고 DEACTIVATED */
  remove: (id) => api.delete(`${base}/${id}`),
});

export const jobGradesApi = settingApi('/job-grades', 'job-grades');
export const jobTitlesApi = settingApi('/job-titles', 'job-titles');
export const employmentTypesApi = settingApi('/employment-types', 'employment-types');
