package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.EmployeeCreateRequest;
import com.nexuslabs.hr.domain.employee.dto.EmployeeCreateResponse;
import com.nexuslabs.hr.domain.employee.dto.EmployeeDetail;
import com.nexuslabs.hr.domain.employee.dto.EmployeeRow;
import com.nexuslabs.hr.domain.employee.service.EmployeeQueryService;
import com.nexuslabs.hr.domain.employee.service.EmployeeRegistrationService;
import com.nexuslabs.hr.domain.employee.service.EmployeeUpdateService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 설계서 6장 — 직원 목록(F-EMP-02) · 등록(F-EMP-01) · 상세(F-EMP-06) · 관리자 수정(F-EMP-03). */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeQueryService queryService;
    private final EmployeeRegistrationService registrationService;
    private final EmployeeUpdateService updateService;

    public EmployeeController(EmployeeQueryService queryService, EmployeeRegistrationService registrationService,
                              EmployeeUpdateService updateService) {
        this.queryService = queryService;
        this.registrationService = registrationService;
        this.updateService = updateService;
    }

    /** 팀 범위면 내가 조직장인 조직과 하위 조직의 직원만 나온다. status 를 비우면 재직 · 휴직만(퇴직자 제외). */
    @GetMapping
    @RequirePermission(PermissionCode.EMPLOYEE_READ)
    public ApiResponse<PageResponse<EmployeeRow>> list(@CurrentUser LoginUser user,
                                                       @RequestParam(required = false) Long orgUnitId,
                                                       @RequestParam(required = false) Long jobGradeId,
                                                       @RequestParam(required = false) Long jobTitleId,
                                                       @RequestParam(required = false) Long employmentTypeId,
                                                       @RequestParam(required = false) List<String> status,
                                                       @RequestParam(required = false) String keyword,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(queryService.list(user, orgUnitId, jobGradeId, jobTitleId,
                employmentTypeId, status, keyword, pageable)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<EmployeeCreateResponse> register(@CurrentUser LoginUser user,
                                                        @Valid @RequestBody EmployeeCreateRequest request) {
        return ApiResponse.ok(registrationService.register(user, request));
    }

    /** 팀 범위면 제한 필드만, 인사 메모는 EMPLOYEE_MANAGE 만. 범위 밖 → OUT_OF_SCOPE. */
    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.EMPLOYEE_READ)
    public ApiResponse<EmployeeDetail> detail(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(queryService.detail(user, id));
    }

    /** 보낸 필드만 바꾼다. 응답은 GET 과 같은 모양. */
    @PatchMapping("/{id}")
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<EmployeeDetail> update(@CurrentUser LoginUser user, @PathVariable long id,
                                              @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(updateService.update(user, id, patch));
    }
}
