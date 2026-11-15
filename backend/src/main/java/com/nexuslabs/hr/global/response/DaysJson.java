package com.nexuslabs.hr.global.response;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 휴가 일수(0.5일 단위, 2026-11-15) — 정수면 지금처럼 15, 반차가 섞이면 14.5 로 쓴다. 반차 전 응답 모양(정수)을 그대로 지키려고
 * BigDecimal 기본 직렬화(14.0 · 1E+1) 대신 쓴다. 휴가 일수 필드에 {@code @JsonSerialize(using = DaysJson.class)}.
 */
public class DaysJson extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        if (value.signum() == 0 || value.stripTrailingZeros().scale() <= 0) {
            gen.writeNumber(value.longValue());
        } else {
            gen.writeNumber(value.setScale(1, RoundingMode.HALF_UP));
        }
    }

    /** Map 에 넣을 때(승인 단계 details 등) — 정수면 Long, 아니면 소수 첫째 자리. */
    public static Number of(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0 ? (Number) value.longValue()
                : value.setScale(1, RoundingMode.HALF_UP);
    }
}
