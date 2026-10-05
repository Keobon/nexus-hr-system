package com.nexuslabs.hr.global.permission;

import com.nexuslabs.hr.global.auth.JwtAuthFilter;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;

/** @RequirePermission 이 붙은 핸들러에서 권한 코드를 확인한다. 실패는 BusinessException → 403. */
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionReader permissionReader;

    public PermissionInterceptor(PermissionReader permissionReader) {
        this.permissionReader = permissionReader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequirePermission required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequirePermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequirePermission.class);
        }
        if (required == null) {
            return true;
        }
        LoginUser user = (LoginUser) request.getAttribute(JwtAuthFilter.LOGIN_USER_ATTR);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        Map<PermissionCode, PermissionScope> granted = permissionReader.permissionsOf(user);
        if (Arrays.stream(required.value()).noneMatch(granted::containsKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
