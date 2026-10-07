package com.nexuslabs.hr.domain.company.controller;

import com.nexuslabs.hr.domain.company.dto.CompanyRegisterRequest;
import com.nexuslabs.hr.domain.company.dto.CompanyRegisterResponse;
import com.nexuslabs.hr.domain.company.service.CompanyRegistrationService;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 비인증 엔드포인트(JwtAuthFilter.PUBLIC). */
@RestController
public class CompanyRegistrationController {

    private final CompanyRegistrationService registrationService;

    public CompanyRegistrationController(CompanyRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/api/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompanyRegisterResponse> register(@Valid @RequestBody CompanyRegisterRequest request) {
        return ApiResponse.ok(registrationService.register(request));
    }
}
