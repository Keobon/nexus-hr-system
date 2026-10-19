// 회사 설정 · 평가 템플릿(API 설계서 12.1, F-EVAL-02) — 항목 · 배점 · 질문. 모두 EVAL_MANAGE
import { api } from './client';

/** GET /eval-templates → [{ id, name, copiedFromId, isActive, inUse, criteriaCount, questionCount }] — 사용 중지 포함 */
export const evalTemplateListKey = ['eval-templates', 'all'];
export const getEvalTemplateList = () => api.get('/eval-templates');

/**
 * GET /eval-templates/{id} → { id, name, copiedFromId, isActive, inUse,
 *   criteria: [{ id, category, name, weight, sortOrder, questions: [{ id, content, sortOrder }] }] }
 * inUse = 시작된 평가 기간에 쓰였다 → 고칠 수 없고 복사해서 고친다
 */
export const evalTemplateKey = (id) => ['eval-templates', 'detail', id];
export const getEvalTemplate = (id) => api.get(`/eval-templates/${id}`);

/**
 * POST · PUT /eval-templates { name, criteria: [{ category, name, weight, sortOrder, questions: [{ content, sortOrder }] }] } — 통째로 저장.
 * 배점 합 100(EVAL_WEIGHT_SUM_INVALID) · 항목마다 질문 1개 이상(EVAL_NO_QUESTION) · 시작된 기간에 쓰였으면 EVAL_TEMPLATE_IN_USE
 */
export const createEvalTemplate = (body) => api.post('/eval-templates', body);
export const updateEvalTemplate = (id, body) => api.put(`/eval-templates/${id}`, body);
/** POST /eval-templates/{id}/copy { name } → 새 템플릿 상세 */
export const copyEvalTemplate = (id, name) => api.post(`/eval-templates/${id}/copy`, { name });
/** DELETE /eval-templates/{id} → { result: DELETED | DEACTIVATED } — 이미 쓰인 템플릿은 사용 중지만 된다 */
export const deleteEvalTemplate = (id) => api.delete(`/eval-templates/${id}`);
