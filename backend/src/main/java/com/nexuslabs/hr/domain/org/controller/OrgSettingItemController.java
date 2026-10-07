package com.nexuslabs.hr.domain.org.controller;

import com.nexuslabs.hr.domain.org.dto.OrgSettingItemRequest;
import com.nexuslabs.hr.domain.org.dto.OrgSettingItemResponse;
import com.nexuslabs.hr.domain.org.service.OrgSettingItemService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** 직급 · 직책 · 고용형태 API 의 공통 모양(API 설계서 5장). 주소는 하위 컨트롤러가 정한다. */
public abstract class OrgSettingItemController {

    private final OrgSettingItemService<?> service;

    protected OrgSettingItemController(OrgSettingItemService<?> service) {
        this.service = service;
    }

    /** 로그인만 하면 볼 수 있다. 등록·발령 화면의 선택지는 activeOnly=true. */
    @GetMapping
    public ApiResponse<List<OrgSettingItemResponse>> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(service.list(activeOnly));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<OrgSettingItemResponse> create(@Valid @RequestBody OrgSettingItemRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PatchMapping("/{id}")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<OrgSettingItemResponse> update(@PathVariable long id,
                                                      @Valid @RequestBody OrgSettingItemRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(service.delete(user, id));
    }
}
