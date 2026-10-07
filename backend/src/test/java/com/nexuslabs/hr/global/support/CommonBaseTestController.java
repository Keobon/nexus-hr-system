package com.nexuslabs.hr.global.support;

import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 공통 기반 통합 테스트 전용 엔드포인트. */
@RestController
@RequestMapping("/api/test")
public class CommonBaseTestController {

    private final ScopeResolver scopeResolver;
    private final AuditLogger auditLogger;
    private final EntityManager em;

    public CommonBaseTestController(ScopeResolver scopeResolver, AuditLogger auditLogger, EntityManager em) {
        this.scopeResolver = scopeResolver;
        this.auditLogger = auditLogger;
        this.em = em;
    }

    @GetMapping("/me")
    public ApiResponse<LoginUser> me(@CurrentUser LoginUser user) {
        return ApiResponse.ok(user);
    }

    @GetMapping("/employees")
    @RequirePermission(PermissionCode.EMPLOYEE_READ)
    public ApiResponse<Map<String, Object>> employees(@CurrentUser LoginUser user) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.EMPLOYEE_READ);
        return ApiResponse.ok(Map.of("all", scope.all(), "ids", scope.employeeIds().stream().sorted().toList()));
    }

    @GetMapping("/employees/{id}")
    @RequirePermission(PermissionCode.EMPLOYEE_READ)
    public ApiResponse<Long> employee(@CurrentUser LoginUser user, @PathVariable long id) {
        scopeResolver.scopeOf(user, PermissionCode.EMPLOYEE_READ).assertContains(id);
        return ApiResponse.ok(id);
    }

    @GetMapping("/payroll")
    @RequirePermission({PermissionCode.PAYROLL_READ, PermissionCode.PAYROLL_MANAGE})
    public ApiResponse<Void> payroll() {
        return ApiResponse.ok();
    }

    @GetMapping("/org-units")
    public ApiResponse<List<String>> orgUnits() {
        return ApiResponse.ok(em.createQuery("SELECT o.name FROM OrgUnit o ORDER BY o.id", String.class)
                .getResultList());
    }

    @GetMapping("/business-error")
    public ApiResponse<Void> businessError() {
        throw new BusinessException(ErrorCode.LEAVE_INSUFFICIENT_BALANCE, Map.of("remaining", 1, "requested", 2));
    }

    @PostMapping("/validate")
    public ApiResponse<Void> validate(@Valid @RequestBody NameRequest request) {
        return ApiResponse.ok();
    }

    @PostMapping("/audit")
    public ApiResponse<Void> audit(@CurrentUser LoginUser user) {
        auditLogger.log(user, AuditAction.UPDATE, "EMPLOYEE", 91103L, Map.of("name", "전"), Map.of("name", "후"));
        return ApiResponse.ok();
    }

    public record NameRequest(@NotBlank(message = "이름을 입력하세요") String name, PermissionCode code) {
    }
}
