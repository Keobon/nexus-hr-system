package com.nexuslabs.hr.global.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");

    private JwtProvider at(Instant instant) {
        return new JwtProvider(SECRET, 8, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void 발급한_토큰에서_직원과_회사를_읽는다() {
        String token = at(NOW).issue(11L, 2L);
        assertThat(at(NOW).parse(token)).isEqualTo(new JwtProvider.Valid(11L, 2L));
    }

    @Test
    void 만료되면_Expired() {
        String token = at(NOW).issue(11L, 2L);
        assertThat(at(NOW.plus(Duration.ofHours(8)).plusSeconds(1)).parse(token)).isInstanceOf(JwtProvider.Expired.class);
    }

    @Test
    void 다른_키로_서명했거나_형식이_틀리면_Invalid() {
        String token = new JwtProvider("other-secret-0123456789abcdef0123456789abcdef", 8, Clock.fixed(NOW, ZoneOffset.UTC))
                .issue(11L, 2L);
        assertThat(at(NOW).parse(token)).isInstanceOf(JwtProvider.Invalid.class);
        assertThat(at(NOW).parse("not-a-token")).isInstanceOf(JwtProvider.Invalid.class);
    }
}
