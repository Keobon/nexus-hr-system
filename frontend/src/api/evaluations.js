// 내 인사발령 · 평가(API 11 · 12장)
import { api } from './client';

/** GET /me/assignments → { current: { orgUnitName, jobGradeName, jobTitleName, employeeNo, lastAssignedDate }, history: [발령, 새 것부터] } */
export const myAssignmentsKey = ['assignments', 'me'];
export const getMyAssignments = () => api.get('/me/assignments');

/** GET /me/evaluations → [{ evaluationId, cycleName, templateName, criteria: [{ name, weight, itemScore, convertedScore }], totalScore, overallComment, confirmedAt }] 확정된 것만 */
export const myEvaluationsKey = ['evaluations', 'me'];
export const getMyEvaluations = () => api.get('/me/evaluations');

/** GET /me/evaluations/todo → 내가 평가자인 건 */
export const myEvalTodoKey = ['evaluations', 'todo'];
export const getMyEvalTodo = () => api.get('/me/evaluations/todo');
