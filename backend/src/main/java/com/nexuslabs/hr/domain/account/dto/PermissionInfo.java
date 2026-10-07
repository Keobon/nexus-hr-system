package com.nexuslabs.hr.domain.account.dto;

import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionScope;

import java.util.List;

/** GET /api/permissions 한 행 — 역할 편집 화면의 권한 표(API 설계서 4장: 코드 · 설명 · 고를 수 있는 범위 · 관련 기능). */
public record PermissionInfo(PermissionCode code, String description, List<PermissionScope> allowedScopes,
                             String relatedFeatures) {
}
