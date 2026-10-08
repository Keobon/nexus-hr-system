package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.file.FileAccessChecker;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 직원 서류 내려받기 권한(API 설계서 14장, BR-FILE-001) — EMPLOYEE_MANAGE · 그 서류의 본인. 이전 버전 파일도 같다.
 * 직원 서류가 참조하지 않는 파일이면 판단하지 않는다(empty).
 */
@Component
class EmployeeDocumentFileAccessChecker implements FileAccessChecker {

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;

    EmployeeDocumentFileAccessChecker(JdbcTemplate jdbc, PermissionReader permissionReader) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
    }

    @Override
    public Optional<Boolean> canRead(LoginUser user, long fileId) {
        List<Long> owners = jdbc.queryForList(
                "SELECT DISTINCT employee_id FROM employee_document WHERE company_id = ? AND file_id = ?",
                Long.class, user.companyId(), fileId);
        if (owners.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(owners.contains(user.employeeId())
                || permissionReader.permissionsOf(user).containsKey(PermissionCode.EMPLOYEE_MANAGE));
    }
}
