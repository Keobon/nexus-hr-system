package com.nexuslabs.hr.domain.leave.controller;

import com.nexuslabs.hr.domain.leave.dto.LeaveTypeRequest;
import com.nexuslabs.hr.domain.leave.dto.LeaveTypeResponse;
import com.nexuslabs.hr.domain.leave.service.LeaveTypeService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 설계서 8장 — 휴가 종류(F-LEAVE-01). */
@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    /** 로그인만 하면 볼 수 있다. 신청 화면은 activeOnly=true. */
    @GetMapping
    public ApiResponse<List<LeaveTypeResponse>> list(@CurrentUser LoginUser user,
                                                     @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(leaveTypeService.list(user, activeOnly));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.LEAVE_MANAGE)
    public ApiResponse<LeaveTypeResponse> create(@CurrentUser LoginUser user,
                                                 @Valid @RequestBody LeaveTypeRequest request) {
        return ApiResponse.ok(leaveTypeService.create(user, request));
    }

    /** 보낸 필드만 바꾼다. null 은 비운다(근속 가산 4개만 비울 수 있다). */
    @PatchMapping("/{id}")
    @RequirePermission(PermissionCode.LEAVE_MANAGE)
    public ApiResponse<LeaveTypeResponse> update(@CurrentUser LoginUser user, @PathVariable long id,
                                                 @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(leaveTypeService.update(user, id, patch));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.LEAVE_MANAGE)
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(leaveTypeService.delete(user, id));
    }
}
