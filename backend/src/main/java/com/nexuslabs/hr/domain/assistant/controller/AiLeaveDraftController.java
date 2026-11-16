package com.nexuslabs.hr.domain.assistant.controller;

import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftDiscardResult;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftRequest;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftResponse;
import com.nexuslabs.hr.domain.assistant.service.AiLeaveDraftService;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestResponse;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 휴가 결재 초안(2026-11-16). 권한 없이 로그인한 직원 누구나 — 본인 휴가만 다룬다.
 * AI 는 초안만 만들고, 실제 신청은 confirm 에서 기존 휴가 신청(POST /api/me/leave-requests)과 같은 로직으로 한다.
 */
@RestController
public class AiLeaveDraftController {

    private final AiLeaveDraftService service;

    public AiLeaveDraftController(AiLeaveDraftService service) {
        this.service = service;
    }

    /** 문장 → 초안(READY · NEED_INFO · INVALID). draftId 를 보내면 그 초안을 고친다. */
    @PostMapping("/api/me/ai/leave-drafts")
    public ApiResponse<AiLeaveDraftResponse> draft(@CurrentUser LoginUser user,
                                                   @Valid @RequestBody AiLeaveDraftRequest request) {
        return ApiResponse.ok(service.draft(user, request));
    }

    /** 초안대로 휴가 신청. 응답은 만들어진 휴가 신청(GET /api/leave-requests/{id} 와 같다). */
    @PostMapping("/api/me/ai/leave-drafts/{id}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeaveRequestResponse> confirm(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(service.confirm(user, id));
    }

    @PostMapping("/api/me/ai/leave-drafts/{id}/discard")
    public ApiResponse<AiLeaveDraftDiscardResult> discard(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(service.discard(user, id));
    }
}
