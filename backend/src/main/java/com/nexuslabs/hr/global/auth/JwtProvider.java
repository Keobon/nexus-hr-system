package com.nexuslabs.hr.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 발급·검증(API 설계서 1.2). 토큰에는 직원 ID(sub)와 회사 ID(cid)만 담는다.
 */
@Component
public class JwtProvider {

    private static final String COMPANY_CLAIM = "cid";

    private final SecretKey key;
    private final Duration validity;
    private final Clock clock;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-hours}") long expirationHours,
                       Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = Duration.ofHours(expirationHours);
        this.clock = clock;
    }

    public String issue(long employeeId, long companyId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(employeeId))
                .claim(COMPANY_CLAIM, companyId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** 검증에 성공하면 직원·회사 ID, 만료면 {@link Expired}, 그 밖의 문제면 {@link Invalid}. */
    public Result parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new Valid(Long.parseLong(claims.getSubject()), claims.get(COMPANY_CLAIM, Long.class));
        } catch (ExpiredJwtException e) {
            return new Expired();
        } catch (JwtException | IllegalArgumentException e) {
            return new Invalid();
        }
    }

    public sealed interface Result permits Valid, Expired, Invalid {}

    public record Valid(long employeeId, long companyId) implements Result {}

    public record Expired() implements Result {}

    public record Invalid() implements Result {}
}
