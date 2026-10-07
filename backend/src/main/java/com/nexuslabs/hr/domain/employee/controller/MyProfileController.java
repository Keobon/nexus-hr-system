package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.MyProfileResponse;
import com.nexuslabs.hr.domain.employee.service.EmployeeQueryService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 6장 — 개인 페이지(F-EMP-05). 본인 수정(PATCH)은 F-EMP-03 에서 만든다. */
@RestController
public class MyProfileController {

    private final EmployeeQueryService queryService;

    public MyProfileController(EmployeeQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/me/profile")
    public ApiResponse<MyProfileResponse> myProfile(@CurrentUser LoginUser user) {
        return ApiResponse.ok(queryService.myProfile(user));
    }
}
