// 인사발령(API 11장) — 조회 ASSIGNMENT_READ, 등록 · 정정 ASSIGNMENT_MANAGE
import { api } from './client';

/** GET /assignments { employeeId, type, from, to, page, size } → 페이지. 새 발령부터 */
export const assignmentsKey = (params) => ['assignments', params];
export const getAssignments = (params) => api.get('/assignments', params);

/** GET /assignments/current — 직원별 지금 조직 · 직급 · 직책(발령 등록 화면이 미리 채우는 값) */
export const currentAssignmentsKey = (params) => ['assignments', 'current', params];
export const getCurrentAssignments = (params) => api.get('/assignments/current', params);

/**
 * GET /promotions/candidates?minYears= — 승진 대상자(전사 범위만). 지금 직급에 N년 이상인 재직 직원, 최고 직급 제외. 페이징
 * 행 { employeeId, employeeNo, name, orgUnitName, jobGradeId, jobGradeName, jobTitleName, gradeSince, yearsInGrade,
 *      averageScore(최근 3년 정기 평가), evaluationCount, promotionScore(최근 승진 고과), promotionCycleName }
 */
export const promotionCandidatesKey = (minYears) => ['assignments', 'promotion-candidates', minYears];
export const getPromotionCandidates = (params) => api.get('/promotions/candidates', params);

/**
 * POST /assignments { employeeId, assignmentType, toOrgUnitId, toJobGradeId, toJobTitleId, reason }
 * 발효일은 서버가 오늘로. 변경 후 값 셋을 모두 보낸다(안 바뀌는 칸은 지금 값).
 */
export const createAssignment = (body) => api.post('/assignments', body);

/** POST /assignments/{id}/corrections { toOrgUnitId, toJobGradeId, toJobTitleId, reason } — 원본은 그대로, 정정 행이 추가된다 */
export const correctAssignment = (id, body) => api.post(`/assignments/${id}/corrections`, body);
