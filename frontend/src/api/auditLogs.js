// 감사 로그(API 3장) — AUDIT_READ. 수정 · 삭제 없음
import { api } from './client';

/**
 * GET /audit-logs?from&to&actorId&targetType&action&page&size → PageResponse<{ id, createdAt, actorId, actorName,
 *   action, targetType, targetId, before, after }> 최신순. actorName 이 null 이면 시스템. 기간은 서울 날짜, 양끝 포함
 */
export const auditLogsKey = (params) => ['audit-logs', params];
export const getAuditLogs = (params) => api.get('/audit-logs', params);
