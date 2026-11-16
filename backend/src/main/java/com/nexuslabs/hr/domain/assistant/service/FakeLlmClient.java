package com.nexuslabs.hr.domain.assistant.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 규칙 기반 가짜 LLM — 테스트(CI 에서 실제 API 를 부르지 않는다)와 API 키 없이 시연할 때(ai.client=fake).
 * 이번 문장에 있는 것만 뽑는다(없는 값은 null → 서버가 지금 초안 값을 유지).
 * <ul>
 *   <li>날짜: 2030-03-11 · 3/11 · 3월 11일 · 오늘 · 내일 · 모레. 두 개면 시작 ~ 끝. 연도가 없으면 올해(반년 넘게 지난 날이면 내년).</li>
 *   <li>종류: 회사 휴가 종류 이름이 문장에 있으면(긴 이름 먼저). 없는데 "반차"면 연차.</li>
 *   <li>반차: "오전" → AM, "오후" → PM, "반차"만 있으면 missing 에 halfDay.</li>
 *   <li>사유: "… 때문에" · "…(으)로 인해" 앞부분에서 날짜를 뺀 것.</li>
 * </ul>
 */
public class FakeLlmClient implements LlmClient {

    private static final Pattern DATE = Pattern.compile(
            "(\d{4})-(\d{1,2})-(\d{1,2})|(?<!\d)(\d{1,2})/(\d{1,2})(?!\d)|(\d{1,2})월\s*(\d{1,2})일|오늘|내일|모레");
    private static final Pattern REASON = Pattern.compile("(.+?)\s*(?:때문에|으로 인해|로 인해)");
    private static final Pattern REASON_EDGE = Pattern.compile("^[\s일에부터까지~,\-]+|[\s,]+$");

    @Override
    public LeaveExtraction extractLeave(LeaveExtractionPrompt prompt) {
        String message = prompt.message();
        List<LocalDate> dates = dates(message, prompt.today());
        String start = dates.isEmpty() ? null : dates.getFirst().toString();
        String end = dates.size() < 2 ? start : dates.get(1).toString();

        String type = prompt.leaveTypeNames().stream()
                .filter(message::contains)
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
        if (type == null && message.contains("반차") && prompt.leaveTypeNames().contains("연차")) {
            type = "연차";
        }

        List<String> missing = new ArrayList<>();
        String halfDay = null;
        if (message.contains("오전")) {
            halfDay = "AM";
        } else if (message.contains("오후")) {
            halfDay = "PM";
        } else if (message.contains("반차")) {
            missing.add("halfDay");
        } else if (message.contains("종일") || message.contains("하루 종일")) {
            halfDay = "NONE";
        }

        return new LeaveExtraction(type, start, end, halfDay, reason(message), missing, null);
    }

    private static List<LocalDate> dates(String message, LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher m = DATE.matcher(message);
        while (m.find()) {
            try {
                LocalDate date = switch (m.group()) {
                    case "오늘" -> today;
                    case "내일" -> today.plusDays(1);
                    case "모레" -> today.plusDays(2);
                    default -> m.group(1) != null
                            ? LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)))
                            : withYear(today, Integer.parseInt(m.group(4) != null ? m.group(4) : m.group(6)),
                            Integer.parseInt(m.group(5) != null ? m.group(5) : m.group(7)));
                };
                dates.add(date);
            } catch (DateTimeException ignored) {
                // 2/30 같은 없는 날짜는 건너뛴다
            }
        }
        return dates;
    }

    private static LocalDate withYear(LocalDate today, int month, int day) {
        LocalDate date = LocalDate.of(today.getYear(), month, day);
        return date.isBefore(today.minusMonths(6)) ? date.plusYears(1) : date;
    }

    private static String reason(String message) {
        Matcher m = REASON.matcher(message);
        if (!m.find()) {
            return null;
        }
        String reason = REASON_EDGE.matcher(DATE.matcher(m.group(1)).replaceAll(" ")).replaceAll("").strip();
        return reason.isEmpty() ? null : reason;
    }
}
