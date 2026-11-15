package com.nexuslabs.hr.global.permission;

import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 권한 상승 막기(2026-11-15, 인사 담당에 ROLE_MANAGE 를 주면서) — 내 권한 범위를 넘는 역할은 만들 수도, 줄 수도,
 * 그 역할의 계정을 다룰 수도 없다. 역할의 모든 권한이 내게 있고, 내 범위가 팀이면 역할도 팀이어야 한다.
 * 최고 관리자 역할은 18개 권한을 모두 가지므로 최고 관리자만 그 역할을 주고 그 계정을 다룬다.
 */
@Component
public class RoleGrantGuard {

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;

    public RoleGrantGuard(JdbcTemplate jdbc, PermissionReader permissionReader) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
    }

    /** 그 역할(같은 회사)의 권한이 모두 내 권한 안에 있어야 한다 — 아니면 FORBIDDEN. */
    public void requireWithin(LoginUser user, long roleId) {
        Map<PermissionCode, PermissionScope> grants = new EnumMap<>(PermissionCode.class);
        jdbc.query("""
                        SELECT permission_code::text AS code, scope::text AS scope
                        FROM role_permission WHERE role_id = ? AND company_id = ?
                        """,
                rs -> {
                    grants.put(PermissionCode.valueOf(rs.getString("code")), PermissionScope.valueOf(rs.getString("scope")));
                },
                roleId, user.companyId());
        requireWithin(user, grants);
    }

    /** 만들거나 바꿀 역할의 권한이 모두 내 권한 안에 있어야 한다 — 아니면 FORBIDDEN. */
    public void requireWithin(LoginUser user, Map<PermissionCode, PermissionScope> grants) {
        if (!covers(permissionReader.permissionsOf(user), grants)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "내 권한보다 넓은 역할은 만들거나 주거나 관리할 수 없습니다");
        }
    }

    /** 회사의 역할마다 내 권한 안인지 — 계정 · 역할 목록이 버튼 · 선택지를 미리 거르게(2026-11-15). */
    public Map<Long, Boolean> withinByRole(LoginUser user) {
        Map<Long, Map<PermissionCode, PermissionScope>> grants = new HashMap<>();
        jdbc.query("""
                        SELECT r.id AS role_id, p.permission_code::text AS code, p.scope::text AS scope
                        FROM role r LEFT JOIN role_permission p ON p.role_id = r.id AND p.company_id = r.company_id
                        WHERE r.company_id = ?
                        """,
                rs -> {
                    Map<PermissionCode, PermissionScope> g = grants.computeIfAbsent(rs.getLong("role_id"),
                            k -> new EnumMap<>(PermissionCode.class));
                    if (rs.getString("code") != null) {
                        g.put(PermissionCode.valueOf(rs.getString("code")), PermissionScope.valueOf(rs.getString("scope")));
                    }
                },
                user.companyId());
        Map<PermissionCode, PermissionScope> mine = permissionReader.permissionsOf(user);
        Map<Long, Boolean> result = new HashMap<>();
        grants.forEach((roleId, g) -> result.put(roleId, covers(mine, g)));
        return result;
    }

    /** grants 의 권한이 모두 mine 에 있고, mine 이 팀 범위면 grants 도 팀 범위. */
    private static boolean covers(Map<PermissionCode, PermissionScope> mine, Map<PermissionCode, PermissionScope> grants) {
        for (Map.Entry<PermissionCode, PermissionScope> grant : grants.entrySet()) {
            PermissionScope have = mine.get(grant.getKey());
            if (have == null || (have == PermissionScope.TEAM && grant.getValue() == PermissionScope.ALL)) {
                return false;
            }
        }
        return true;
    }
}
