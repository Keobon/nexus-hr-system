package com.nexuslabs.hr.domain.company.controller;

import com.nexuslabs.hr.domain.company.dto.HolidayRequest;
import com.nexuslabs.hr.domain.company.dto.HolidayResponse;
import com.nexuslabs.hr.domain.company.service.HolidayService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
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

/** API 설계서 3장 — 휴일(F-COMP-05). */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    /** 로그인만 하면 본다. year 를 비우면 올해. 매년 반복 휴일은 그 해 날짜로 펼쳐서 내려간다. */
    @GetMapping
    public ApiResponse<List<HolidayResponse>> list(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(holidayService.list(year));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<HolidayResponse> create(@CurrentUser LoginUser user, @Valid @RequestBody HolidayRequest request) {
        return ApiResponse.ok(holidayService.create(user, request));
    }

    /** 보낸 필드만 바꾼다. "안 보냄"과 "null"을 구별해야 해서 본문을 Map 으로 받는다. */
    @PatchMapping("/{id}")
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<HolidayResponse> update(@CurrentUser LoginUser user, @PathVariable long id,
                                               @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(holidayService.update(user, id, patch));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<Void> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        holidayService.delete(user, id);
        return ApiResponse.ok();
    }
}
