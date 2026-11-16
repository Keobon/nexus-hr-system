package com.nexuslabs.hr.domain.notification.controller;

import com.nexuslabs.hr.domain.notification.dto.NotificationItem;
import com.nexuslabs.hr.domain.notification.service.NotificationService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 내 알림(B1, 2026-11-16). 권한 없이 로그인한 직원 누구나 — 자기 알림만. */
@RestController
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** 안 읽은 것 먼저, 최신 먼저. unreadOnly=true 면 안 읽은 것만. */
    @GetMapping("/api/me/notifications")
    public ApiResponse<PageResponse<NotificationItem>> mine(@CurrentUser LoginUser user,
                                                            @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.mine(user, unreadOnly, pageable)));
    }

    /** 읽음(멱등). 남의 알림은 404. */
    @PostMapping("/api/me/notifications/{id}/read")
    public ApiResponse<NotificationItem> read(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(service.markRead(user, id));
    }

    /**
     * 알림 스트림(SSE). 인증은 다른 API 와 같은 Authorization 헤더(fetch 기반 SSE 클라이언트).
     * 연결 직후 event: unread-count {"unreadCount": n} 한 번, 이후 새 알림마다 event: notification(NotificationItem),
     * 25초마다 주석 줄(:ping). 공통 응답 래퍼로 감싸지 않는다.
     */
    @GetMapping(path = "/api/me/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser LoginUser user) {
        return service.connect(user);
    }
}
