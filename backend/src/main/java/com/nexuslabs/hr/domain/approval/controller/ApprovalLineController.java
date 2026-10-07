package com.nexuslabs.hr.domain.approval.controller;

import com.nexuslabs.hr.domain.approval.dto.ApprovalLineRequest;
import com.nexuslabs.hr.domain.approval.dto.ApprovalLineResponse;
import com.nexuslabs.hr.domain.approval.service.ApprovalLineService;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 9.1 — 승인선 관리. */
@RestController
@RequestMapping("/api/approval-lines")
@RequirePermission(PermissionCode.APPROVAL_MANAGE)
public class ApprovalLineController {

    private final ApprovalLineService lineService;

    public ApprovalLineController(ApprovalLineService lineService) {
        this.lineService = lineService;
    }

    @GetMapping
    public ApiResponse<List<ApprovalLineResponse>> list(@CurrentUser LoginUser user,
                                                        @RequestParam(required = false) ApprovalWorkType workType) {
        return ApiResponse.ok(lineService.list(user, workType));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApprovalLineResponse> create(@CurrentUser LoginUser user,
                                                    @Valid @RequestBody ApprovalLineRequest request) {
        return ApiResponse.ok(lineService.create(user, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApprovalLineResponse> update(@CurrentUser LoginUser user, @PathVariable long id,
                                                    @Valid @RequestBody ApprovalLineRequest request) {
        return ApiResponse.ok(lineService.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(lineService.delete(user, id));
    }
}
