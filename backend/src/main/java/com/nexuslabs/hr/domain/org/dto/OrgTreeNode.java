package com.nexuslabs.hr.domain.org.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * 조직도의 한 노드(GET /api/org-units/tree). directCount 는 그 조직 직속, totalCount 는 하위 조직까지 합친 인원이다(퇴직자 제외).
 * monthlyBudget 은 ORG_MANAGE 가 있을 때만 내려간다 — 권한이 없으면 필드 자체가 빠지고(Java null),
 * 권한이 있는데 예산이 없으면 null 로 내려간다(빈 Optional). API 설계서 1.1.
 */
public record OrgTreeNode(long id, String name, String levelName, @JsonProperty("isActive") boolean active,
                          LeadSummary lead, int sortOrder,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Optional<Long> monthlyBudget,
                          int directCount, int totalCount, List<OrgTreeNode> children) {
}
