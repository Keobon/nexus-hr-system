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

/**
 * GET /evaluations/{id} — 평가 입력 폼(F-EVAL-04). { id, cycleName, cycleStatus, targetName, orgUnitName, evaluatorName, templateName, status,
 *   overallComment, reopenReason, submittedAt, confirmedAt, editable, criteria: [{ id, category, name, weight, itemScore, convertedScore,
 *   questions: [{ id, content, score }] }], totalScore } — 점수는 그 항목 질문에 모두 답해야, 총점은 모든 항목 점수가 있어야 나온다
 */
export const evalFormKey = (id) => ['evaluations', 'detail', id];
export const getEvalForm = (id) => api.get(`/evaluations/${id}`);
/** PUT /evaluations/{id}/answers { answers: [{ questionId, score 1–5 }], overallComment } — 임시저장(보낸 답으로 통째로 바꾼다) */
export const saveEvalAnswers = (id, body) => api.put(`/evaluations/${id}/answers`, body);
/** POST /evaluations/{id}/submit — 모든 질문 답 + 종합 의견이 있어야 한다(EVAL_INCOMPLETE). 기간이 끝났으면 EVAL_PERIOD_CLOSED(재오픈은 예외) */
export const submitEvaluation = (id) => api.post(`/evaluations/${id}/submit`);
