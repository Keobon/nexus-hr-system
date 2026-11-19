package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 클라이언트 고르기와 규칙 기반 가짜 클라이언트. 스프링 없이 돈다. */
class LlmClientTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 3, 4);
    private static final List<String> TYPES = List.of("연차", "병가", "경조사 휴가");

    @Test
    void 키가_없거나_꺼져_있으면_AI_UNAVAILABLE() {
        LlmClient noKey = AiClientConfig.choose(true, "anthropic", "", "claude-opus-5-5", Duration.ofSeconds(5), null);
        assertThat(noKey.available()).isFalse();
        assertThatThrownBy(() -> noKey.extractLeave(new LeaveExtractionPrompt(TODAY, TYPES, "연차", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
        assertThat(AiClientConfig.choose(false, "fake", "", "m", Duration.ofSeconds(5), null).available()).isFalse();
        assertThat(AiClientConfig.choose(true, "fake", "", "m", Duration.ofSeconds(5), null))
                .isInstanceOf(FakeLlmClient.class);
    }

    @Test
    void 어긋난_응답의_missing은_빈_목록_null_제외로() {
        assertThat(new LeaveExtraction(null, null, null, null, null, null, null).missing()).isEmpty();
        assertThat(new LeaveExtraction(null, null, null, null, null,
                java.util.Arrays.asList("halfDay", null), null).missing()).containsExactly("halfDay");
    }

    private static LeaveExtraction fake(String message) {
        return new FakeLlmClient().extractLeave(new LeaveExtractionPrompt(TODAY, TYPES, message, null));
    }

    @Test
    void 가짜_클라이언트는_날짜_종류_반차_사유를_뽑는다() {
        assertThat(fake("3월 11일 병원 진료 때문에 병가 써줘"))
                .isEqualTo(new LeaveExtraction("병가", "2030-03-11", "2030-03-11", null, "병원 진료", List.of(), null));
        assertThat(fake("2030-03-11부터 2030-03-13까지 경조사 휴가"))
                .extracting(LeaveExtraction::leaveTypeName, LeaveExtraction::startDate, LeaveExtraction::endDate)
                .containsExactly("경조사 휴가", "2030-03-11", "2030-03-13");
        assertThat(fake("내일 오전 반차")).extracting(LeaveExtraction::leaveTypeName, LeaveExtraction::startDate,
                LeaveExtraction::halfDay).containsExactly("연차", "2030-03-05", "AM");
        assertThat(fake("내일 반차").missing()).containsExactly("halfDay");
        // 반년 넘게 지난 날짜는 내년
        assertThat(new FakeLlmClient().extractLeave(new LeaveExtractionPrompt(LocalDate.of(2030, 11, 16), TYPES,
                "1/2 연차", null)).startDate()).isEqualTo("2031-01-02");
        assertThat(fake("1/2 연차").startDate()).isEqualTo("2030-01-02");
        assertThat(fake("2/30 연차").startDate()).isNull();
    }

    @Test
    void 꺼져_있으면_분류_연장근무_출장도_AI_UNAVAILABLE() {
        LlmClient off = LlmClient.DISABLED;
        assertThatThrownBy(() -> off.classify(TODAY, "연차"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
        assertThatThrownBy(() -> off.extractOvertime(new OvertimeExtractionPrompt(TODAY, "야근", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
        assertThatThrownBy(() -> off.extractTrip(new TripExtractionPrompt(TODAY, "출장", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    private static OvertimeExtraction overtime(String message) {
        return new FakeLlmClient().extractOvertime(new OvertimeExtractionPrompt(TODAY, message, null));
    }

    private static TripExtraction trip(String message) {
        return new FakeLlmClient().extractTrip(new TripExtractionPrompt(TODAY, message, null));
    }

    @Test
    void 가짜_클라이언트는_연장근무_날짜_시각_사유를_뽑는다() {
        assertThat(overtime("내일 19:00~22:00 배포 작업 때문에 연장근무"))
                .isEqualTo(new OvertimeExtraction("2030-03-05", "19:00", "22:00", "배포 작업", List.of(), null));
        // 오전 · 오후 표시가 없는 1–11시는 오후, "반"은 30분, 표시가 있으면 그대로
        assertThat(overtime("3/6 7시 반부터 10시까지 야근"))
                .extracting(OvertimeExtraction::plannedStart, OvertimeExtraction::plannedEnd)
                .containsExactly("19:30", "22:00");
        assertThat(overtime("밤 11시부터 새벽 1시까지 점검으로 야근"))
                .extracting(OvertimeExtraction::plannedStart, OvertimeExtraction::plannedEnd, OvertimeExtraction::reason)
                .containsExactly("23:00", "01:00", "점검");
        assertThat(overtime("3/6 연장근무").plannedStart()).isNull();
    }

    @Test
    void 가짜_클라이언트는_출장_목적지_종류_비용을_뽑는다() {
        assertThat(trip("3/11~3/12 부산 출장, 거래처 미팅"))
                .isEqualTo(new TripExtraction("부산", "거래처 미팅", "2030-03-11", "2030-03-12", "DOMESTIC", null,
                        List.of(), null));
        assertThat(trip("3/18 도쿄로 출장, 비용 150만 원"))
                .extracting(TripExtraction::destination, TripExtraction::tripType, TripExtraction::estimatedCost)
                .containsExactly("도쿄", "OVERSEAS", 1_500_000L);
        // 모르는 지명이면 출장 종류를 되묻는다
        assertThat(trip("3/11 가나다시 출장, 점검").missing()).containsExactly("tripType");
        // 목적지가 없다("때문에"는 지명이 아니다)
        assertThat(trip("3/11 거래처 미팅 때문에 출장"))
                .extracting(TripExtraction::destination, TripExtraction::purpose)
                .containsExactly(null, "거래처 미팅");
    }

    @Test
    void 가짜_클라이언트_업무_분류() {
        FakeLlmClient fake = new FakeLlmClient();
        assertThat(fake.classify(TODAY, "오늘 잔업 좀 할게요")).isEqualTo(AiWorkType.OVERTIME);
        assertThat(fake.classify(TODAY, "대전 출장 신청")).isEqualTo(AiWorkType.BUSINESS_TRIP);
        assertThat(fake.classify(TODAY, "병가 쓰려고요")).isEqualTo(AiWorkType.LEAVE);
        assertThat(fake.classify(TODAY, "안녕")).isEqualTo(AiWorkType.UNKNOWN);
    }
}
