package com.nexuslabs.hr.domain.account.service;

import com.nexuslabs.hr.domain.account.dto.MeResponse;
import com.nexuslabs.hr.domain.account.dto.PermissionInfo;
import com.nexuslabs.hr.domain.account.dto.RoleDetail;
import com.nexuslabs.hr.domain.account.dto.RoleListItem;
import com.nexuslabs.hr.domain.account.dto.RoleRequest;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionScope;
import com.nexuslabs.hr.global.response.DeleteResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 역할 관리(F-AUTH-05). 권한은 요청마다 DB에서 읽으므로 저장하면 즉시 반영된다(BR-ROLE-003).
 * 최고 관리자 역할은 시스템 역할이라 바꿀 수 없다(BR-ROLE-001).
 */
@Service
public class RoleService {

    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;

    public RoleService(JdbcTemplate jdbc, AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
    }

    public List<PermissionInfo> permissions() {
        return Arrays.stream(PermissionCode.values())
                .map(c -> new PermissionInfo(c, c.description(),
                        c.allowedScopes().stream().sorted().toList(), c.relatedFeatures()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleListItem> list(LoginUser user) {
        return jdbc.query("""
                        SELECT r.id, r.name, r.description, r.is_system,
                               (SELECT count(*) FROM account a WHERE a.role_id = r.id AND a.company_id = r.company_id) AS account_count
                        FROM role r WHERE r.company_id = ?
                        ORDER BY r.is_system DESC, r.id
                        """,
                (rs, i) -> new RoleListItem(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getBoolean("is_system"), rs.getLong("account_count")),
                user.companyId());
    }

    @Transactional(readOnly = true)
    public RoleDetail get(LoginUser user, long roleId) {
        RoleListItem role = find(user.companyId(), roleId);
        return new RoleDetail(role.id(), role.name(), role.description(), role.isSystem(), role.accountCount(),
                grants(user.companyId(), roleId));
    }

    @Transactional
    public RoleDetail create(LoginUser user, RoleRequest request) {
        validatePermissions(request.permissions());
        checkNameUnique(user.companyId(), request.name().trim(), null);
        long roleId = jdbc.queryForObject(
                "INSERT INTO role (company_id, name, description, is_system) VALUES (?, ?, ?, FALSE) RETURNING id",
                Long.class, user.companyId(), request.name().trim(), blankToNull(request.description()));
        insertGrants(user.companyId(), roleId, request.permissions());
        RoleDetail created = get(user, roleId);
        auditLogger.log(user, AuditAction.CREATE, "ROLE", roleId, null, created);
        return created;
    }

    @Transactional
    public RoleDetail update(LoginUser user, long roleId, RoleRequest request) {
        RoleDetail before = get(user, roleId);
        if (before.isSystem()) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_READONLY);
        }
        validatePermissions(request.permissions());
        checkNameUnique(user.companyId(), request.name().trim(), roleId);
        jdbc.update("UPDATE role SET name = ?, description = ?, updated_at = now() WHERE id = ? AND company_id = ?",
                request.name().trim(), blankToNull(request.description()), roleId, user.companyId());
        jdbc.update("DELETE FROM role_permission WHERE role_id = ? AND company_id = ?", roleId, user.companyId());
        insertGrants(user.companyId(), roleId, request.permissions());
        RoleDetail after = get(user, roleId);
        auditLogger.log(user, AuditAction.UPDATE, "ROLE", roleId, before, after);
        return after;
    }

    /** 계정이 배정됐거나 승인선 조건에 쓰인 역할은 지울 수 없다. 역할은 비활성화 개념이 없어 항상 DELETED. */
    @Transactional
    public DeleteResult delete(LoginUser user, long roleId) {
        RoleDetail before = get(user, roleId);
        if (before.isSystem()) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_READONLY);
        }
        if (before.accountCount() > 0) {
            throw new BusinessException(ErrorCode.ITEM_IN_USE, "계정이 배정된 역할입니다. 다른 역할로 옮긴 뒤 삭제하세요",
                    Map.of("accountCount", before.accountCount()));
        }
        Boolean usedByApprovalLine = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM approval_line WHERE company_id = ? AND cond_role_id = ?)",
                Boolean.class, user.companyId(), roleId);
        if (Boolean.TRUE.equals(usedByApprovalLine)) {
            throw new BusinessException(ErrorCode.ITEM_IN_USE, "승인선 조건에 쓰인 역할입니다. 승인선에서 먼저 빼세요");
        }
        jdbc.update("DELETE FROM role WHERE id = ? AND company_id = ?", roleId, user.companyId());
        auditLogger.log(user, AuditAction.DELETE, "ROLE", roleId, before, null);
        return DeleteResult.deleted();
    }

    private RoleListItem find(long companyId, long roleId) {
        return jdbc.query("""
                        SELECT r.id, r.name, r.description, r.is_system,
                               (SELECT count(*) FROM account a WHERE a.role_id = r.id AND a.company_id = r.company_id) AS account_count
                        FROM role r WHERE r.id = ? AND r.company_id = ?
                        """,
                (rs, i) -> new RoleListItem(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getBoolean("is_system"), rs.getLong("account_count")),
                roleId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private List<MeResponse.PermissionGrant> grants(long companyId, long roleId) {
        return jdbc.query("""
                        SELECT permission_code::text AS code, scope::text AS scope
                        FROM role_permission WHERE role_id = ? AND company_id = ?
                        """,
                (rs, i) -> new MeResponse.PermissionGrant(PermissionCode.valueOf(rs.getString("code")),
                        PermissionScope.valueOf(rs.getString("scope"))),
                roleId, companyId).stream()
                .sorted(Comparator.comparing(MeResponse.PermissionGrant::code))
                .toList();
    }

    /** 같은 코드 두 번, 전사 전용 코드에 팀 범위 → VALIDATION_ERROR (DB CHECK 전에 알기 쉬운 메시지로). */
    private static void validatePermissions(List<RoleRequest.Grant> grants) {
        Set<PermissionCode> seen = EnumSet.noneOf(PermissionCode.class);
        for (RoleRequest.Grant g : grants) {
            if (!seen.add(g.code())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "같은 권한 코드가 두 번 있습니다",
                        Map.of("code", g.code().name()));
            }
            if (!g.code().allowedScopes().contains(g.scope())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이 권한은 전사 범위만 고를 수 있습니다",
                        Map.of("code", g.code().name(), "allowedScopes", g.code().allowedScopes()));
            }
        }
    }

    private void checkNameUnique(long companyId, String name, Long exceptRoleId) {
        Boolean taken = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM role WHERE company_id = ? AND name = ? AND id <> COALESCE(?, -1))
                        """,
                Boolean.class, companyId, name, exceptRoleId);
        if (Boolean.TRUE.equals(taken)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
    }

    private void insertGrants(long companyId, long roleId, List<RoleRequest.Grant> grants) {
        jdbc.batchUpdate("""
                        INSERT INTO role_permission (company_id, role_id, permission_code, scope)
                        VALUES (?, ?, ?::permission_code, ?::perm_scope)
                        """,
                grants, grants.size(), (ps, g) -> {
                    ps.setLong(1, companyId);
                    ps.setLong(2, roleId);
                    ps.setString(3, g.code().name());
                    ps.setString(4, g.scope().name());
                });
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
