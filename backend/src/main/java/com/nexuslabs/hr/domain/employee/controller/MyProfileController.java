package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.MyProfileResponse;
import com.nexuslabs.hr.domain.employee.service.EmployeeQueryService;
import com.nexuslabs.hr.domain.employee.service.MyProfileService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** API 설계서 6장 — 개인 페이지(F-EMP-05)와 본인 정보 수정(F-EMP-03 의 본인 범위). */
@RestController
public class MyProfileController {

    private final EmployeeQueryService queryService;
    private final MyProfileService profileService;

    public MyProfileController(EmployeeQueryService queryService, MyProfileService profileService) {
        this.queryService = queryService;
        this.profileService = profileService;
    }

    @GetMapping("/api/me/profile")
    public ApiResponse<MyProfileResponse> myProfile(@CurrentUser LoginUser user) {
        return ApiResponse.ok(queryService.myProfile(user));
    }

    /** 보낸 필드만 바꾼다. 응답은 GET 과 같은 모양. */
    @PatchMapping("/api/me/profile")
    public ApiResponse<MyProfileResponse> update(@CurrentUser LoginUser user, @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(profileService.update(user, patch));
    }
}
