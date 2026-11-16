package com.nexuslabs.hr.domain.notification.service;

/**
 * 테스트가 SSE 연결 수 확인 · heartbeat 즉시 실행(25초 기다리지 않음) · 연결 정리를 하도록 여는 창구.
 * 컴포넌트 스캔에 걸리지 않게 애너테이션 없이 두고, 쓰는 테스트가 @Import 한다.
 */
public class SseEmitterRegistryAccess {

    private final SseEmitterRegistry registry;

    public SseEmitterRegistryAccess(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    public int connections(long companyId, long employeeId) {
        return registry.connections(companyId, employeeId);
    }

    public void heartbeat() {
        registry.heartbeat();
    }

    public void disconnect(long companyId, long employeeId) {
        registry.disconnect(companyId, employeeId);
    }
}
