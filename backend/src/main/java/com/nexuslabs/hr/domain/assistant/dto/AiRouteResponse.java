package com.nexuslabs.hr.domain.assistant.dto;

import com.nexuslabs.hr.domain.assistant.service.AiWorkType;

/**
 * POST /api/me/ai/route 응답 — LEAVE · OVERTIME · BUSINESS_TRIP 이면 그 초안 API 로 같은 문장을 보내고,
 * UNKNOWN 이면 챗봇이 어떤 신청인지 되묻는다. 초안은 만들지 않는다.
 */
public record AiRouteResponse(AiWorkType workType) {
}
