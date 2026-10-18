// 인사 · 평가 관리(API 설계서 12.1 · 12.2) — 평가 기간 · 대상 규칙 · 시작 · 마감 · 진행 현황 · 확정 · 재오픈. 기간 · 진행은 EVAL_MANAGE, 결과는 EVAL_READ
import { api } from './client';

/** GET /eval-cycles → [{ id, name, startDate, endDate, status SCHEDULED|IN_PROGRESS|CLOSED, startedAt, closedAt, evaluationCount }] */
export const evalCyclesKey = ['eval-cycles'];
export const getEvalCycles = () => api.get('/eval-cycles');
/** POST /eval-cycles { name, startDate, endDate } → 다른 기간과 겹치면 warning PERIOD_OVERLAP(저장은 됨) */
export const createEvalCycle = (body) => api.post('/eval-cycles', body);
/** PATCH /eval-cycles/{id} — 예정일 때 이름 · 날짜 */
export const updateEvalCycle = (id, patch) => api.patch(`/eval-cycles/${id}`, patch);

/** GET · PUT /eval-cycles/{id}/targets { rules: [{ priority, condIsOrgLead, condJobTitleId, condJobGradeId, condEmploymentTypeId, evalTemplateId(null=제외) }] } — 위에서부터 처음 맞는 규칙 */
export const evalTargetsKey = (id) => ['eval-cycles', id, 'targets'];
export const getEvalTargets = (id) => api.get(`/eval-cycles/${id}/targets`);
export const putEvalTargets = (id, body) => api.put(`/eval-cycles/${id}/targets`, body);
/** GET /eval-cycles/{id}/targets/preview → { targets: [{ employeeId, name, orgUnitName, evalTemplateName, evaluatorName }], excluded: [{ name, reason }] } */
export const evalTargetsPreviewKey = (id) => ['eval-cycles', id, 'preview'];
export const getEvalTargetsPreview = (id) => api.get(`/eval-cycles/${id}/targets/preview`);
/** POST /eval-cycles/{id}/start — 대상마다 평가를 만든다(대상이 없으면 EVAL_NO_TARGET). POST .../close — 마감 */
export const startEvalCycle = (id) => api.post(`/eval-cycles/${id}/start`);
export const closeEvalCycle = (id) => api.post(`/eval-cycles/${id}/close`);

/**
 * GET /eval-cycles/{id}/progress → { counts: { NOT_STARTED, IN_PROGRESS, SUBMITTED, CONFIRMED, REOPENED },
 *   items: [{ evaluationId, targetId, targetName, orgUnitName, evaluatorName, templateName, status, totalScore(제출 · 확정만), needsEvaluatorChange }] }
 */
export const evalProgressKey = (id) => ['eval-cycles', id, 'progress'];
export const getEvalProgress = (id) => api.get(`/eval-cycles/${id}/progress`);

/** GET /evaluations/{id} — 평가자 · EVAL_MANAGE. { cycleName, targetName, orgUnitName, evaluatorName, templateName, status, overallComment, submittedAt, confirmedAt, criteria: [{ name, weight, itemScore, convertedScore }], totalScore } */
export const evaluationKey = (id) => ['evaluations', 'detail', id];
export const getEvaluation = (id) => api.get(`/evaluations/${id}`);
/** POST /evaluations/{id}/confirm — 제출완료 → 확정(본인 공개). POST .../reopen { reason } — 확정 → 재오픈(평가자가 다시 제출) */
export const confirmEvaluation = (id) => api.post(`/evaluations/${id}/confirm`);
export const reopenEvaluation = (id, reason) => api.post(`/evaluations/${id}/reopen`, { reason });

/** GET /evaluations/results?cycleId&orgUnitId — 범위 안 확정 결과(EVAL_READ, 조직장은 자기 팀). 행: { evaluationId, cycleName, employeeNo, targetName, orgUnitName, templateName, evaluatorName, criteria, totalScore, confirmedAt } */
export const evalResultsKey = (params) => ['evaluations', 'results', params];
export const getEvalResults = (params) => api.get('/evaluations/results', params);

/** GET /eval-templates?activeOnly=true → [{ id, name, isActive, criteriaCount, questionCount }] — 평가 유형 선택지 */
export const evalTemplatesKey = ['eval-templates', 'active'];
export const getEvalTemplates = () => api.get('/eval-templates', { activeOnly: true });
