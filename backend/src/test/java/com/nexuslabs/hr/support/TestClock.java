package com.nexuslabs.hr.support;

import com.nexuslabs.hr.global.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 시각에 따라 결과가 달라지는 기능(출퇴근 · 근태 계산)의 테스트용 시계. @Import 한 테스트에서만 실제 시계 대신 쓰인다.
 * 테스트가 {@link MutableClock#set}으로 "지금"을 옮긴다. 시계를 옮기면 전에 발급한 토큰이 만료될 수 있으니 요청마다 새로 발급한다.
 */
@TestConfiguration
public class TestClock {

    /** 2030-03-04 는 월요일이다. 처음에는 이날 09:00 이다. */
    public static final LocalDate MONDAY = LocalDate.of(2030, 3, 4);

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }

    public static final class MutableClock extends Clock {

        private volatile Instant instant = MONDAY.atTime(9, 0).atZone(ClockConfig.ZONE).toInstant();

        public void set(LocalDateTime time) {
            instant = time.atZone(ClockConfig.ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ClockConfig.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
