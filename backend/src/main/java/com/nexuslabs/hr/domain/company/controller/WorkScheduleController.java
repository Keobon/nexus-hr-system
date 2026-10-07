package com.nexuslabs.hr.domain.company.controller;

import com.nexuslabs.hr.domain.company.dto.WorkScheduleList;
import com.nexuslabs.hr.domain.company.dto.WorkScheduleRequest;
import com.nexuslabs.hr.domain.company.dto.WorkScheduleResponse;
import com.nexuslabs.hr.domain.company.service.WorkScheduleService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 3장 — 근무시간(F-COMP-04). 수정 API 는 없다. */
@RestController
@RequestMapping("/api/work-schedules")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;

    public WorkScheduleController(WorkScheduleService workScheduleService) {
        this.workScheduleService = workScheduleService;
    }

    /** 로그인만 하면 본다. */
    @GetMapping
    public ApiResponse<WorkScheduleList> list() {
        return ApiResponse.ok(workScheduleService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<WorkScheduleResponse> create(@CurrentUser LoginUser user,
                                                    @Valid @RequestBody WorkScheduleRequest request) {
        return ApiResponse.ok(workScheduleService.create(user, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<Void> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        workScheduleService.delete(user, id);
        return ApiResponse.ok();
    }
}
