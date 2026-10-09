package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.global.audit.AuditAction;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/** 감사 로그 한 줄(API 설계서 3장 GET /audit-logs). actorId · actorName 이 null 이면 시스템 처리. before · after 는 기록된 JSON 그대로. */
public record AuditLogItem(long id, OffsetDateTime createdAt, Long actorId, String actorName, AuditAction action,
                           String targetType, Long targetId, JsonNode before, JsonNode after) {
}
