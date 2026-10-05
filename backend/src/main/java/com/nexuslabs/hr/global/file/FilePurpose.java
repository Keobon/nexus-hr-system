package com.nexuslabs.hr.global.file;

import com.nexuslabs.hr.global.permission.PermissionCode;

import java.util.Set;

/**
 * 업로드 용도(API 설계서 14장). DB에는 저장하지 않는다 — 올릴 때 권한·형식 검사에만 쓴다.
 * uploadPermission 이 null 이면 로그인만 하면 올릴 수 있다.
 */
public enum FilePurpose {
    RECEIPT(null, FileType.ALL),
    COMPANY_DOCUMENT(PermissionCode.COMPANY_MANAGE, FileType.ALL),
    EMPLOYEE_DOCUMENT(PermissionCode.EMPLOYEE_MANAGE, FileType.ALL),
    PROFILE(null, FileType.IMAGES),
    LOGO(PermissionCode.COMPANY_MANAGE, FileType.IMAGES);

    private final PermissionCode uploadPermission;
    private final Set<FileType> allowedTypes;

    FilePurpose(PermissionCode uploadPermission, Set<FileType> allowedTypes) {
        this.uploadPermission = uploadPermission;
        this.allowedTypes = allowedTypes;
    }

    public PermissionCode uploadPermission() { return uploadPermission; }

    public Set<FileType> allowedTypes() { return allowedTypes; }
}
