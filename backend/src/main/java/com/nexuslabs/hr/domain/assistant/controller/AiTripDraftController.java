package com.nexuslabs.hr.domain.assistant.controller;

import com.nexuslabs.hr.domain.assistant.dto.AiDraftRequest;
import com.nexuslabs.hr.domain.assistant.dto.AiLeaveDraftDiscardResult;
import com.nexuslabs.hr.domain.assistant.dto.AiTripDraftResponse;
import com.nexuslabs.hr.domain.assistant.service.AiTripDraftService;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripResponse;
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
 * AI 출장 결재 초안(2026-11-19). 권한 없이 로그인한 직원 누구나 — 본인 신청만 다룬다.
 * AI 는 초안만 만들고, 실제 신청은 confirm 에서 기존 출장 신청(POST /api/me/business-trips)과 같은 로직으로 한다.
 */
@RestController
public class AiTripDraftController {

    private final AiTripDraftService service;

    public AiTripDraftController(AiTripDraftService service) {
        this.service = service;
    }

    /** 문장 → 초안(READY · NEED_INFO · INVALID). draftId 를 보내면 그 초안을 고친다. */
    @PostMapping("/api/me/ai/trip-drafts")
    public ApiResponse<AiTripDraftResponse> draft(@CurrentUser LoginUser user,
                                                  @Valid @RequestBody AiDraftRequest request) {
        return ApiResponse.ok(service.draft(user, request));
    }

    /** 초안대로 출장 신청. 응답은 만들어진 출장 신청(GET /api/business-trips/{id} 와 같다). */
    @PostMapping("/api/me/ai/trip-drafts/{id}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusinessTripResponse> confirm(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(service.confirm(user, id));
    }

    @PostMapping("/api/me/ai/trip-drafts/{id}/discard")
    public ApiResponse<AiLeaveDraftDiscardResult> discard(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(service.discard(user, id));
    }
}
