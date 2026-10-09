package com.nexuslabs.hr.domain.company.controller;

import com.nexuslabs.hr.domain.company.dto.AuditLogItem;
import com.nexuslabs.hr.domain.company.service.AuditLogService;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API 설계서 3장 — 감사 로그 조회(F-COMP-06). */
@RestController
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/api/audit-logs")
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ApiResponse<PageResponse<AuditLogItem>> list(@CurrentUser LoginUser user,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) Long actorId,
                                                        @RequestParam(required = false) String targetType,
                                                        @RequestParam(required = false) AuditAction action,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                auditLogService.list(user, from, to, actorId, targetType, action, pageable)));
    }
}
