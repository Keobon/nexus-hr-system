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
}
