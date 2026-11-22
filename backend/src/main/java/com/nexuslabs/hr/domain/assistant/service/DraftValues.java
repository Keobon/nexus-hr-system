package com.nexuslabs.hr.domain.assistant.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 가 준 값 다시 검사하기 — 휴가 · 연장근무 · 출장 초안이 같이 쓴다. AI 응답은 사용자 입력과 같은 수준으로 믿지 않는다.
 * 형식이 틀리면 null(모름)로 바꿔 되묻게 한다.
 */
final class DraftValues {

    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})$");

    private DraftValues() {
    }

    /** YYYY-MM-DD 가 아니면 null. */
    static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** 오늘부터 1년 안팎인지(지난 날짜 사후 신청 포함). */
    static boolean inRange(LocalDate date, LocalDate today) {
        return !date.isBefore(today.minusYears(1)) && !date.isAfter(today.plusYears(1));
    }

    /** "H:mm" · "HH:mm"(00:00–23:59) → "HH:mm". 아니면 null. */
    static String time(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = TIME.matcher(value.strip());
        if (!m.matches()) {
            return null;
        }
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        return h > 23 || min > 59 ? null : "%02d:%02d".formatted(h, min);
    }

    /** 제어 문자를 빼고 앞뒤 공백을 지우고 max 자로 자른다. 비면 null. */
    static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String s = value.replaceAll("\\p{Cntrl}", " ").strip();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
