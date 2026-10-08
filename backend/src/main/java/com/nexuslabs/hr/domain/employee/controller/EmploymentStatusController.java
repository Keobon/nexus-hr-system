package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.EmploymentStatusItem;
import com.nexuslabs.hr.domain.employee.dto.EmploymentStatusRequest;
import com.nexuslabs.hr.domain.employee.service.EmploymentStatusService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 6장 — 재직상태 변경 · 이력(F-EMP-04). */
@RestController
public class EmploymentStatusController {

    private final EmploymentStatusService employmentStatusService;

    public EmploymentStatusController(EmploymentStatusService employmentStatusService) {
        this.employmentStatusService = employmentStatusService;
    }

    /** 응답은 방금 추가된 이력 한 줄. 퇴직이면 계정 · 조직장 · 대기 신청 · 승인 단계까지 같은 트랜잭션에서 처리된다. */
    @PostMapping("/api/employees/{id}/status")
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<EmploymentStatusItem> change(@CurrentUser LoginUser user, @PathVariable long id,
                                                    @Valid @RequestBody EmploymentStatusRequest request) {
        return ApiResponse.ok(employmentStatusService.change(user, id, request));
    }

    /** 시간순. EMPLOYEE_READ 전사 범위만. */
    @GetMapping("/api/employees/{id}/status-history")
    @RequirePermission(PermissionCode.EMPLOYEE_READ)
    public ApiResponse<List<EmploymentStatusItem>> history(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(employmentStatusService.history(user, id));
    }
}
