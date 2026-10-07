package com.nexuslabs.hr.domain.account.dto;

import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionScope;

import java.util.List;

/** GET /api/permissions 한 행 — 역할 편집 화면의 권한 표. description 에 관련 기능이 적혀 있다. */
public record PermissionInfo(PermissionCode code, String description, List<PermissionScope> allowedScopes) {
}
