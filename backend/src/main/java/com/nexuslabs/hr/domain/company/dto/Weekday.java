package com.nexuslabs.hr.domain.company.dto;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 근무 요일. API 는 요일 배열로 주고받고 DB 는 비트값(work_days)으로 저장한다 — 월 1 · 화 2 · 수 4 · 목 8 · 금 16 · 토 32 · 일 64.
 * DB ENUM 이 아니다.
 */
public enum Weekday {
    MON(DayOfWeek.MONDAY), TUE(DayOfWeek.TUESDAY), WED(DayOfWeek.WEDNESDAY), THU(DayOfWeek.THURSDAY),
    FRI(DayOfWeek.FRIDAY), SAT(DayOfWeek.SATURDAY), SUN(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    Weekday(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    private int bit() {
        return 1 << ordinal();
    }

    public static short toBits(Collection<Weekday> days) {
        return (short) days.stream().mapToInt(Weekday::bit).reduce(0, (a, b) -> a | b);
    }

    public static List<Weekday> fromBits(int bits) {
        return Arrays.stream(values()).filter(d -> (bits & d.bit()) != 0).toList();
    }

    public static Set<DayOfWeek> daysOfWeek(int bits) {
        return fromBits(bits).stream().map(d -> d.dayOfWeek).collect(Collectors.toUnmodifiableSet());
    }
}
