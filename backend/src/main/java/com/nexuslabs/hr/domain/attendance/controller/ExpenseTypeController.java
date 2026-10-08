package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.ExpenseTypeRequest;
import com.nexuslabs.hr.domain.attendance.dto.ExpenseTypeResponse;
import com.nexuslabs.hr.domain.attendance.service.ExpenseTypeService;
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

/** API 설계서 7.3 — 출장 경비 종류(F-ATT-09). */
@RestController
@RequestMapping("/api/expense-types")
public class ExpenseTypeController {

    private final ExpenseTypeService expenseTypeService;

    public ExpenseTypeController(ExpenseTypeService expenseTypeService) {
        this.expenseTypeService = expenseTypeService;
    }

    /** 로그인만 하면 볼 수 있다. 경비 청구 화면의 선택지는 activeOnly=true. */
    @GetMapping
    public ApiResponse<List<ExpenseTypeResponse>> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(expenseTypeService.list(activeOnly));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ATTENDANCE_MANAGE)
    public ApiResponse<ExpenseTypeResponse> create(@CurrentUser LoginUser user,
                                                   @Valid @RequestBody ExpenseTypeRequest request) {
        return ApiResponse.ok(expenseTypeService.create(user, request));
    }

    /** 보낸 필드만 바꾼다. */
    @PatchMapping("/{id}")
    @RequirePermission(PermissionCode.ATTENDANCE_MANAGE)
    public ApiResponse<ExpenseTypeResponse> update(@CurrentUser LoginUser user, @PathVariable long id,
                                                   @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(expenseTypeService.update(user, id, patch));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.ATTENDANCE_MANAGE)
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(expenseTypeService.delete(user, id));
    }
}
