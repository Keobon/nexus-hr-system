package com.nexuslabs.hr.domain.org.dto;

/** 조직 소속 직원 한 명(GET /api/org-units/{id}/members). 상세 정보는 직원 상세 API 에서 권한에 따라 본다. */
public record OrgMemberResponse(long id, String name, String jobGradeName, String jobTitleName, Long profileFileId) {
}
