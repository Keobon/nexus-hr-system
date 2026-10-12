package com.nexuslabs.hr.domain.org.dto;

import com.nexuslabs.hr.domain.attendance.service.TodayStatus;

/**
 * 조직 소속 직원 한 명(GET /api/org-units/{id}/members). 상세 정보는 직원 상세 API 에서 권한에 따라 본다.
 * orgUnitId · orgUnitName 은 직원의 소속 조직이다(includeSub 면 하위 조직일 수 있다).
 * workStatus 는 오늘 어디서 일하는지(OFFICE · REMOTE · FIELD · BUSINESS_TRIP · ON_VACATION)만, 그 밖에는 null 이다.
 */
public record OrgMemberResponse(long id, String name, String jobGradeName, String jobTitleName, Long profileFileId,
                                long orgUnitId, String orgUnitName, String email, TodayStatus workStatus) {

    public OrgMemberResponse withWorkStatus(TodayStatus workStatus) {
        return new OrgMemberResponse(id, name, jobGradeName, jobTitleName, profileFileId, orgUnitId, orgUnitName, email,
                workStatus);
    }
}
