package com.nexuslabs.hr.domain.account.controller;

import com.nexuslabs.hr.domain.account.dto.LoginRequest;
import com.nexuslabs.hr.domain.account.dto.LoginResponse;
import com.nexuslabs.hr.domain.account.dto.PasswordChangeRequest;
import com.nexuslabs.hr.domain.account.service.AuthService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 서버에 세션이 없으므로 클라이언트가 토큰을 버리면 끝난다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@CurrentUser LoginUser user, @Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(user, request);
        return ApiResponse.ok();
    }
}
