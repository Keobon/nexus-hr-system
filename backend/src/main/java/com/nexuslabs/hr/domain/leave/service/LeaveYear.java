package com.nexuslabs.hr.domain.leave.service;

import java.time.LocalDate;

/**
 * 휴가 연도 = 회사의 회계연도(기능명세서 1.5). 회계연도 시작월부터 12개월이고, 시작하는 해의 숫자로 부른다.
 * 예: 시작월 4 → 2026 휴가 연도 = 2026-04-01 ~ 2027-03-31.
 */
public record LeaveYear(int year, LocalDate start, LocalDate end) {

    public static LeaveYear of(int year, int startMonth) {
        LocalDate start = LocalDate.of(year, startMonth, 1);
        return new LeaveYear(year, start, start.plusYears(1).minusDays(1));
    }

    /** date 가 속한 휴가 연도. */
    public static LeaveYear containing(LocalDate date, int startMonth) {
        int year = date.getMonthValue() >= startMonth ? date.getYear() : date.getYear() - 1;
        return of(year, startMonth);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
