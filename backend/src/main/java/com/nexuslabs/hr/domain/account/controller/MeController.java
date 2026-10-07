package com.nexuslabs.hr.domain.account.controller;

import com.nexuslabs.hr.domain.account.dto.MeResponse;
import com.nexuslabs.hr.domain.account.service.MeService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 변경 전에도 부를 수 있다(JwtAuthFilter). */
@RestController
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/api/me")
    public ApiResponse<MeResponse> me(@CurrentUser LoginUser user) {
        return ApiResponse.ok(meService.me(user));
    }
}
