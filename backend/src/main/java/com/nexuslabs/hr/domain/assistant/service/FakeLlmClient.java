package com.nexuslabs.hr.domain.assistant.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 규칙 기반 가짜 LLM — 테스트(CI 에서 실제 API 를 부르지 않는다)와 API 키 없이 시연할 때(ai.client=fake).
 * 이번 문장에 있는 것만 뽑는다(없는 값은 null → 서버가 지금 초안 값을 유지).
 * <ul>
 *   <li>날짜: 2030-03-11 · 3/11 · 3월 11일 · 오늘 · 내일 · 모레. 두 개면 시작 ~ 끝. 연도가 없으면 올해(반년 넘게 지난 날이면 내년).</li>
 *   <li>휴가 종류: 회사 휴가 종류 이름이 문장에 있으면(긴 이름 먼저). 없는데 "반차"면 연차.</li>
 *   <li>반차: "오전" → AM, "오후" → PM, "반차"만 있으면 missing 에 halfDay.</li>
 *   <li>사유 · 목적: "… 때문에" · "…(으)로 인해" 앞부분에서 날짜 · 시각 · 목적지를 뺀 것.</li>
 *   <li>분류: 연장 · 야근 · 잔업 → OVERTIME, 출장 → BUSINESS_TRIP, 휴가 · 연차 · 반차 · 병가 → LEAVE, 그 밖에 UNKNOWN.</li>
 *   <li>연장근무 시각: "19:00~22:00" 그대로, "7시부터 10시까지"처럼 오전 · 오후 표시가 없는 1–11시는 오후.</li>
 *   <li>출장: "부산 출장" · "부산으로 출장"의 앞 단어가 목적지, 아는 국내 · 해외 지명이면 출장 종류, "N만 원" · "N원"이 예상 비용.</li>
 * </ul>
 */
public class FakeLlmClient implements LlmClient {

    private static final Pattern DATE = Pattern.compile(
            "(\\d{4})-(\\d{1,2})-(\\d{1,2})|(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d)|(\\d{1,2})월\\s*(\\d{1,2})일|오늘|내일|모레");
    private static final Pattern REASON = Pattern.compile("(.+?)\\s*(?:때문에|으로 인해|로 인해)");
    private static final Pattern REASON_EDGE = Pattern.compile("^[\\s일에부터까지~,\\-]+|[\\s,]+$");

    // 연장근무
    private static final Pattern CLOCK_RANGE = Pattern.compile(
            "(?<!\\d)(\\d{1,2}):(\\d{2})\\s*(?:~|-|부터)\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern HOUR_RANGE = Pattern.compile(
            "(오전|오후|저녁|밤|새벽)?\\s*(\\d{1,2})시(\\s*반|\\s*(\\d{1,2})분)?\\s*(?:부터|~|-)\\s*"
                    + "(오전|오후|저녁|밤|새벽)?\\s*(\\d{1,2})시(\\s*반|\\s*(\\d{1,2})분)?");
    private static final Pattern TIME_TOKEN = Pattern.compile(
            "(오전|오후|저녁|밤|새벽)?\\s*\\d{1,2}(:\\d{2}|시(\\s*반|\\s*\\d{1,2}분)?)\\s*(부터|까지|~|-)?");
    private static final Pattern OVERTIME_REASON = Pattern.compile("(.+?)\\s*(?:으로|로)\\s*(?:야근|연장|잔업)");
    private static final Pattern OVERTIME_WORDS = Pattern.compile("연장\\s*근무|연장|야근|잔업");

    // 출장
    // 게으른 매칭 — "도쿄로 출장"에서 "도쿄로"가 아니라 "도쿄"
    private static final Pattern DESTINATION = Pattern.compile("([가-힣A-Za-z]{2,}?)\\s*(?:으로|로|에)?\\s*출장");
    private static final Set<String> NOT_PLACE = Set.of("국내", "해외", "이번", "다음", "다음주", "이번주", "당일", "오늘",
            "내일", "모레", "주말", "연차", "휴가", "때문에", "때문", "위해", "하러", "목적");
    private static final Set<String> DOMESTIC = Set.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "제주",
            "수원", "성남", "판교", "용인", "고양", "천안", "청주", "전주", "창원", "포항", "경주", "강릉", "춘천", "여수", "목포", "구미",
            "평택", "안산", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남");
    private static final Set<String> OVERSEAS = Set.of("미국", "일본", "중국", "베트남", "싱가포르", "대만", "홍콩", "태국",
            "독일", "영국", "프랑스", "캐나다", "호주", "인도", "도쿄", "오사카", "후쿠오카", "베이징", "상하이", "하노이", "호치민",
            "타이베이", "방콕", "뉴욕", "샌프란시스코", "실리콘밸리", "런던", "파리", "베를린", "시드니", "LA");
    private static final Pattern PURPOSE_LABEL = Pattern.compile("목적(?:은|:)\\s*(.+)$");
    private static final Pattern AFTER_COMMA = Pattern.compile("[,，]\\s*(.+?)\\s*$");
    private static final Pattern COST_MAN = Pattern.compile("(\\d+)\\s*만\\s*원");
    private static final Pattern COST_WON = Pattern.compile("(?<![\\d/])(\\d[\\d,]*)\\s*원");

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

    @Override
    public AiWorkType classify(LocalDate today, String message) {
        if (containsAny(message, "연장", "야근", "잔업")) {
            return AiWorkType.OVERTIME;
        }
        if (message.contains("출장")) {
            return AiWorkType.BUSINESS_TRIP;
        }
        if (containsAny(message, "휴가", "연차", "반차", "병가")) {
            return AiWorkType.LEAVE;
        }
        return AiWorkType.UNKNOWN;
    }

    @Override
    public OvertimeExtraction extractOvertime(OvertimeExtractionPrompt prompt) {
        String message = prompt.message();
        List<LocalDate> dates = dates(message, prompt.today());
        String workDate = dates.isEmpty() ? null : dates.getFirst().toString();

        String start = null;
        String end = null;
        Matcher clock = CLOCK_RANGE.matcher(message);
        Matcher hour = HOUR_RANGE.matcher(message);
        if (clock.find()) {
            start = hhmm(Integer.parseInt(clock.group(1)), Integer.parseInt(clock.group(2)));
            end = hhmm(Integer.parseInt(clock.group(3)), Integer.parseInt(clock.group(4)));
        } else if (hour.find()) {
            start = hhmm(hour24(hour.group(1), Integer.parseInt(hour.group(2))), minute(hour.group(3), hour.group(4)));
            end = hhmm(hour24(hour.group(5), Integer.parseInt(hour.group(6))), minute(hour.group(7), hour.group(8)));
        }

        String reason = cleanPart(REASON, message);
        if (reason == null) {
            reason = cleanPart(OVERTIME_REASON, message);
        }
        return new OvertimeExtraction(workDate, start, end, reason, List.of(), null);
    }

    @Override
    public TripExtraction extractTrip(TripExtractionPrompt prompt) {
        String message = prompt.message();
        List<LocalDate> dates = dates(message, prompt.today());
        String start = dates.isEmpty() ? null : dates.getFirst().toString();
        // 날짜가 하나면 종료일은 이번 문장에 없다(null) — 새 초안이면 서버가 하루짜리로, 고치는 중이면 기존 종료일을 유지한다
        String end = dates.size() < 2 ? null : dates.get(1).toString();

        String destination = null;
        Matcher d = DESTINATION.matcher(message);
        while (d.find()) {
            if (!NOT_PLACE.contains(d.group(1))) {
                destination = d.group(1);
                break;
            }
        }

        List<String> missing = new ArrayList<>();
        String tripType = null;
        if (message.contains("해외") || (destination != null && OVERSEAS.contains(destination))) {
            tripType = "OVERSEAS";
        } else if (message.contains("국내") || (destination != null && DOMESTIC.contains(destination))) {
            tripType = "DOMESTIC";
        } else if (destination != null) {
            missing.add("tripType");
        }

        String purpose = purpose(message, destination);
        return new TripExtraction(destination, purpose, start, end, tripType, cost(message), missing, null);
    }

    // ------------------------------------------------------------------

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

    /** pattern 의 첫 그룹에서 날짜 · 시각 · 연장근무 낱말을 뺀 것. 비면 null. */
    private static String cleanPart(Pattern pattern, String message) {
        Matcher m = pattern.matcher(message);
        if (!m.find()) {
            return null;
        }
        String s = DATE.matcher(m.group(1)).replaceAll(" ");
        s = TIME_TOKEN.matcher(s).replaceAll(" ");
        s = OVERTIME_WORDS.matcher(s).replaceAll(" ");
        s = REASON_EDGE.matcher(s.replaceAll("\\s+", " ")).replaceAll("").strip();
        return s.isEmpty() ? null : s;
    }

    /** 출장 목적 — "… 때문에", "목적은 …", 쉼표 뒤 순서로. 날짜 · 목적지 · "출장"은 뺀다. */
    private static String purpose(String message, String destination) {
        String raw = null;
        Matcher m = REASON.matcher(message);
        if (m.find()) {
            raw = m.group(1);
        } else if ((m = PURPOSE_LABEL.matcher(message)).find() || (m = AFTER_COMMA.matcher(message)).find()) {
            raw = m.group(1);
        }
        if (raw == null) {
            return null;
        }
        String s = DATE.matcher(raw).replaceAll(" ");
        if (destination != null) {
            s = s.replace(destination, " ");
        }
        s = s.replaceAll("(?:으로|로|에)?\\s*출장", " ").replaceAll("\\s+", " ");
        s = REASON_EDGE.matcher(s).replaceAll("").strip();
        return s.isEmpty() ? null : s;
    }

    private static Long cost(String message) {
        Matcher man = COST_MAN.matcher(message);
        if (man.find()) {
            return Long.parseLong(man.group(1)) * 10_000;
        }
        Matcher won = COST_WON.matcher(message);
        if (won.find()) {
            try {
                return Long.parseLong(won.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 오전 · 오후 표시가 없는 1–11시는 오후(연장근무 맥락). 밤 12시는 0시. */
    private static int hour24(String marker, int h) {
        if (marker == null) {
            return h >= 1 && h <= 11 ? h + 12 : h;
        }
        return switch (marker) {
            case "오후", "저녁" -> h < 12 ? h + 12 : h;
            case "밤" -> h == 12 ? 0 : (h < 12 && h >= 6 ? h + 12 : h);
            default -> h; // 오전 · 새벽
        };
    }

    private static int minute(String part, String minutes) {
        if (part == null) {
            return 0;
        }
        return minutes != null ? Integer.parseInt(minutes) : 30; // "반"
    }

    /** 범위를 벗어나면 그대로 두고 서버가 형식 검사에서 버린다. */
    private static String hhmm(int h, int m) {
        return "%02d:%02d".formatted(h, m);
    }

    private static boolean containsAny(String message, String... words) {
        for (String w : words) {
            if (message.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
