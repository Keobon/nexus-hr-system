// 인사발령(API 11장) — 조회 ASSIGNMENT_READ, 등록 · 정정 ASSIGNMENT_MANAGE
import { api } from './client';

/** GET /assignments { employeeId, type, from, to, page, size } → 페이지. 새 발령부터 */
export const assignmentsKey = (params) => ['assignments', params];
export const getAssignments = (params) => api.get('/assignments', params);

/** GET /assignments/current — 직원별 지금 조직 · 직급 · 직책(발령 등록 화면이 미리 채우는 값) */
export const currentAssignmentsKey = (params) => ['assignments', 'current', params];
export const getCurrentAssignments = (params) => api.get('/assignments/current', params);

/**
 * POST /assignments { employeeId, assignmentType, toOrgUnitId, toJobGradeId, toJobTitleId, reason }
 * 발효일은 서버가 오늘로. 변경 후 값 셋을 모두 보낸다(안 바뀌는 칸은 지금 값).
 */
export const createAssignment = (body) => api.post('/assignments', body);

/** POST /assignments/{id}/corrections { toOrgUnitId, toJobGradeId, toJobTitleId, reason } — 원본은 그대로, 정정 행이 추가된다 */
export const correctAssignment = (id, body) => api.post(`/assignments/${id}/corrections`, body);
