package com.nexuslabs.hr.domain.assistant.controller;

import com.nexuslabs.hr.domain.assistant.dto.AiRouteRequest;
import com.nexuslabs.hr.domain.assistant.dto.AiRouteResponse;
import com.nexuslabs.hr.domain.assistant.service.AiRouteService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AI 비서 업무 분류(2026-11-19) — 칩을 고르지 않고 바로 쓴 문장을 휴가 · 연장근무 · 출장 중 어디로 보낼지. */
@RestController
public class AiRouteController {

    private final AiRouteService service;

    public AiRouteController(AiRouteService service) {
        this.service = service;
    }

    @PostMapping("/api/me/ai/route")
    public ApiResponse<AiRouteResponse> route(@CurrentUser LoginUser user, @Valid @RequestBody AiRouteRequest request) {
        return ApiResponse.ok(service.route(user, request.message()));
    }
}
