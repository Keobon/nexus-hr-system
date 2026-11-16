package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 직원당 1분에 LLM 호출 N번(ai.rate-limit-per-minute, 기본 10)까지. 넘으면 AI_RATE_LIMITED.
 * 서버 한 대 기준(메모리) — 서버를 여러 대로 늘리면 공용 저장소로 옮긴다.
 */
@Component
public class AiRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private final int limit;
    private final Clock clock;
    private final Map<Long, Deque<Long>> calls = new ConcurrentHashMap<>();

    public AiRateLimiter(@Value("${ai.rate-limit-per-minute:10}") int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public void acquire(long employeeId) {
        long now = clock.millis();
        Deque<Long> times = calls.computeIfAbsent(employeeId, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MILLIS) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
            }
            times.addLast(now);
        }
    }
}
