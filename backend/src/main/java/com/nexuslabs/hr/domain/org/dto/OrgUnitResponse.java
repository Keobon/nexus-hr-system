package com.nexuslabs.hr.domain.org.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 조직 한 건(등록 · 수정 · 이동 · 활성 전환의 응답). 최상위 조직은 parentId 가 null 이다. */
public record OrgUnitResponse(long id, Long parentId, String name, String levelName, LeadSummary lead,
                              Long monthlyBudget, int sortOrder, @JsonProperty("isActive") boolean active) {
}
