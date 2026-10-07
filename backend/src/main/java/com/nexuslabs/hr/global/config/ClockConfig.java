package com.nexuslabs.hr.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** "오늘·지금"은 이 Clock에서 가져온다(백엔드 안내 6.1). 테스트에서 고정 Clock으로 바꿀 수 있다. */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
