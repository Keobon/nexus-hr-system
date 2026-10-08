package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.file.FileAccessChecker;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 회사 서류 내려받기 권한(API 설계서 14장, BR-FILE-001) — COMPANY_MANAGE. 이전 버전 파일도 같다.
 * 회사 서류가 참조하지 않는 파일이면 판단하지 않는다(empty).
 */
@Component
class CompanyDocumentFileAccessChecker implements FileAccessChecker {

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;

    CompanyDocumentFileAccessChecker(JdbcTemplate jdbc, PermissionReader permissionReader) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
    }

    @Override
    public Optional<Boolean> canRead(LoginUser user, long fileId) {
        Boolean referenced = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM company_document WHERE company_id = ? AND file_id = ?)",
                Boolean.class, user.companyId(), fileId);
        if (!Boolean.TRUE.equals(referenced)) {
            return Optional.empty();
        }
        return Optional.of(permissionReader.permissionsOf(user).containsKey(PermissionCode.COMPANY_MANAGE));
    }
}
