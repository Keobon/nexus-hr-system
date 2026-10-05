package com.nexuslabs.hr.global.permission;

import com.nexuslabs.hr.global.auth.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.EnumMap;
import java.util.Map;

/**
 * 역할의 권한을 DB에서 읽는다(토큰에는 권한이 없다 — 역할을 바꾸면 즉시 반영).
 * 한 요청 안에서는 한 번만 읽도록 요청 속성에 담아 둔다.
 */
@Component
public class PermissionReader {

    private static final String CACHE_ATTR = "hr.permissions";

    private final JdbcTemplate jdbc;

    public PermissionReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @SuppressWarnings("unchecked")
    public Map<PermissionCode, PermissionScope> permissionsOf(LoginUser user) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object cached = attrs.getAttribute(CACHE_ATTR, RequestAttributes.SCOPE_REQUEST);
            if (cached != null) {
                return (Map<PermissionCode, PermissionScope>) cached;
            }
        }
        Map<PermissionCode, PermissionScope> result = new EnumMap<>(PermissionCode.class);
        jdbc.query("""
                        SELECT permission_code::text AS code, scope::text AS scope
                        FROM role_permission WHERE role_id = ? AND company_id = ?
                        """,
                rs -> {
                    result.put(PermissionCode.valueOf(rs.getString("code")),
                            PermissionScope.valueOf(rs.getString("scope")));
                },
                user.roleId(), user.companyId());
        if (attrs != null) {
            attrs.setAttribute(CACHE_ATTR, result, RequestAttributes.SCOPE_REQUEST);
        }
        return result;
    }
}
