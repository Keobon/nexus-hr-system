package com.nexuslabs.hr.domain.notification.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 접속 중인 알림 스트림(SSE) 연결. (회사, 직원)마다 여러 개(탭 여러 개)를 둔다.
 * 25초마다 heartbeat(주석 줄)를 보내 프록시가 연결을 끊지 않게 하고, 보내다 실패한 연결 · 시간이 지난 연결은 뺀다.
 * <p>
 * <b>서버 1대 전제</b> — 연결을 이 서버 메모리에만 둔다. 서버를 여러 대로 늘리면 다른 서버에 붙은 직원에게는
 * 실시간 전송이 안 된다(알림 행은 DB 에 있으니 목록에는 나온다). 그때는 공용 저장소(Redis pub/sub 등)로 옮긴다.
 */
@Component
public class SseEmitterRegistry {

    public static final String EVENT_NOTIFICATION = "notification";
    public static final String EVENT_UNREAD_COUNT = "unread-count";

    /** 30분 뒤 서버가 끊는다 — 클라이언트가 다시 연결한다. */
    static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    static final long HEARTBEAT_SECONDS = 25;

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final Map<Key, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
        emitters.values().forEach(set -> set.forEach(SseEmitter::complete));
        emitters.clear();
    }

    /** 새 연결. 끝나거나(complete) 시간이 지나거나 오류가 나면 저절로 빠진다. */
    public SseEmitter connect(long companyId, long employeeId) {
        Key key = new Key(companyId, employeeId);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> {
            remove(key, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(key, emitter));
        return emitter;
    }

    /** 한 연결에만 보낸다(연결 직후 안 읽은 개수). */
    public void sendTo(long companyId, long employeeId, SseEmitter emitter, String name, Object data) {
        trySend(new Key(companyId, employeeId), emitter, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
    }

    /** 그 직원의 모든 연결에 보낸다. 보낸 연결 수(접속 중이 아니면 0). */
    public int send(long companyId, long employeeId, String name, Object data) {
        Key key = new Key(companyId, employeeId);
        int sent = 0;
        for (SseEmitter emitter : List.copyOf(emitters.getOrDefault(key, Set.of()))) {
            if (trySend(key, emitter, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON))) {
                sent++;
            }
        }
        return sent;
    }

    /** 모든 연결에 주석 줄(:ping). 실패한 연결은 뺀다. */
    void heartbeat() {
        for (Map.Entry<Key, Set<SseEmitter>> entry : List.copyOf(emitters.entrySet())) {
            for (SseEmitter emitter : List.copyOf(entry.getValue())) {
                trySend(entry.getKey(), emitter, SseEmitter.event().comment("ping"));
            }
        }
    }

    int connections(long companyId, long employeeId) {
        return emitters.getOrDefault(new Key(companyId, employeeId), Set.of()).size();
    }

    /** 그 직원의 연결을 모두 끝낸다. */
    void disconnect(long companyId, long employeeId) {
        Set<SseEmitter> set = emitters.remove(new Key(companyId, employeeId));
        if (set != null) {
            set.forEach(SseEmitter::complete);
        }
    }

    private boolean trySend(Key key, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            // 클라이언트가 끊었거나 이미 끝난 연결
            remove(key, emitter);
            log.debug("sse send failed type={}", e.getClass().getSimpleName());
            return false;
        }
    }

    private void remove(Key key, SseEmitter emitter) {
        emitters.computeIfPresent(key, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    private record Key(long companyId, long employeeId) {
    }
}
