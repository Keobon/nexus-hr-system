package com.nexuslabs.hr.global.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드(또는 클래스)에 붙인다. 나열한 코드 중 하나라도 있으면 통과, 없으면 403 FORBIDDEN.
 * 범위(팀/전사)는 여기서 보지 않는다 — 서비스에서 ScopeResolver로 좁힌다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    PermissionCode[] value();
}
