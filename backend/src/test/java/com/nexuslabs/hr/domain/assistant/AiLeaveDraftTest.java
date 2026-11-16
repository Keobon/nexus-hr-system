package com.nexuslabs.hr.domain.assistant;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestClock;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 휴가 결재 초안(2026-11-16). 테스트는 규칙 기반 FakeLlmClient(ai.client=fake)만 쓴다 — 실제 Claude API 를 부르지 않는다.
 * 시계는 2030-03-04(월) 09:00, 조현우에게 2030년 연차 10일. 휴가 기본 승인선은 조현우 → 서예린 → 강하늘.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class AiLeaveDraftTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, cid);
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2030, 'REGULAR', 10)""", cid, org.cho.id(), annual);
    }

    private ResultActions draft(TestFixture.Employee e, String message, Long draftId) throws Exception {
        String body = draftId == null
                ? "{\"message\": \"%s\"}".formatted(message)
                : "{\"message\": \"%s\", \"draftId\": %d}".formatted(message, draftId);
        return mvc.perform(post("/api/me/ai/leave-drafts").header("Authorization", fixture.token(e.id(), cid))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions action(TestFixture.Employee e, long draftId, String action) throws Exception {
        return mvc.perform(post("/api/me/ai/leave-drafts/" + draftId + "/" + action)
                .header("Authorization", fixture.token(e.id(), cid)));
    }

    private static long draftId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.draftId"))
                .longValue();
    }

    private String draftStatus(long draftId) {
        return jdbc.queryForObject("SELECT status::text FROM ai_leave_draft WHERE id = ?", String.class, draftId);
    }

    @Test
    void 종류_날짜_사유가_다_있으면_READY_미리보기까지() throws Exception {
        draft(org.cho, "3/11 병원 진료 때문에 연차 써줘", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.question").doesNotExist())
                .andExpect(jsonPath("$.data.draft.leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.draft.startDate").value("2030-03-11"))
                .andExpect(jsonPath("$.data.draft.endDate").value("2030-03-11"))
                .andExpect(jsonPath("$.data.draft.reason").value("병원 진료"))
                .andExpect(jsonPath("$.data.preview.days").value(1))
                .andExpect(jsonPath("$.data.preview.balance.remainingAfter").value(9))
                .andExpect(jsonPath("$.data.errors").isEmpty());
        // 초안만 — 휴가 신청은 아직 없다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request WHERE company_id = ?", Long.class, cid))
                .isZero();
    }

    @Test
    void 종류가_없으면_NEED_INFO로_되묻고_답하면_READY() throws Exception {
        ResultActions first = draft(org.cho, "3/11에 쉬고 싶어", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"))
                .andExpect(jsonPath("$.data.question", notNullValue()))
                .andExpect(jsonPath("$.data.draft.startDate").value("2030-03-11"))
                .andExpect(jsonPath("$.data.preview").doesNotExist());
        long id = draftId(first);
        draft(org.cho, "연차로 해줘", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value(id))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.draft.startDate").value("2030-03-11"));
    }

    @Test
    void 없는_종류나_오전_오후를_모르는_반차는_되묻는다() throws Exception {
        draft(org.cho, "3/11 안식 휴가 쓸게", null)
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"));
        long id = draftId(draft(org.cho, "3/11 반차 쓸게", null)
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"))
                .andExpect(jsonPath("$.data.question").value("오전 반차인가요, 오후 반차인가요?")));
        draft(org.cho, "오후로", id)
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.halfDay").value("PM"))
                .andExpect(jsonPath("$.data.preview.days").value(0.5));
    }

    @Test
    void 잔여가_부족하면_INVALID() throws Exception {
        // 3/11(월) ~ 3/25(월) = 근무일 11일 > 잔여 10일
        draft(org.cho, "3/11~3/25 가족 여행 때문에 연차", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andExpect(jsonPath("$.data.errors", hasItem("LEAVE_INSUFFICIENT_BALANCE")))
                .andExpect(jsonPath("$.data.preview.days").value(11));
    }

    @Test
    void 확인하면_기존_로직으로_휴가_신청이_생긴다() throws Exception {
        long id = draftId(draft(org.cho, "3/11 병원 진료 때문에 연차 써줘", null));
        String body = action(org.cho, id, "confirm")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeId").value(org.cho.id()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.startDate").value("2030-03-11"))
                .andExpect(jsonPath("$.data.reason").value("병원 진료"))
                .andReturn().getResponse().getContentAsString();
        long leaveId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(draftStatus(id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT leave_request_id FROM ai_leave_draft WHERE id = ?", Long.class, id))
                .isEqualTo(leaveId);
        // 승인 단계도 기존 신청과 똑같이 열린다
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM approval_step WHERE company_id = ? AND work_type = 'LEAVE' AND target_id = ?""",
                Long.class, cid, leaveId)).isPositive();
        // 같은 초안으로 두 번 신청할 수 없다
        action(org.cho, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 남의_초안은_확인_폐기_수정할_수_없다() throws Exception {
        long id = draftId(draft(org.cho, "3/11 병원 진료 때문에 연차 써줘", null));
        action(org.seo, id, "confirm").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AI_DRAFT_NOT_FOUND"));
        action(org.seo, id, "discard").andExpect(status().isNotFound());
        draft(org.seo, "3/12로 바꿔줘", id).andExpect(status().isNotFound());
        assertThat(draftStatus(id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request WHERE company_id = ?", Long.class, cid))
                .isZero();
    }

    @Test
    void 만료된_초안은_확인할_수_없다() throws Exception {
        long id = draftId(draft(org.cho, "3/11 병원 진료 때문에 연차 써줘", null));
        clock.set(MONDAY.atTime(9, 31));
        action(org.cho, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AI_DRAFT_EXPIRED"));
        assertThat(draftStatus(id)).isEqualTo("EXPIRED");
        draft(org.cho, "3/12로 바꿔줘", id).andExpect(jsonPath("$.error.code").value("AI_DRAFT_EXPIRED"));
    }

    @Test
    void 폐기한_초안은_확인할_수_없다() throws Exception {
        long id = draftId(draft(org.cho, "3/11 병원 진료 때문에 연차 써줘", null));
        action(org.cho, id, "discard").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISCARDED"));
        action(org.cho, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 직원당_1분에_10번까지() throws Exception {
        for (int i = 0; i < 10; i++) {
            draft(org.yoon, "3/11 연차", null).andExpect(status().isOk());
        }
        draft(org.yoon, "3/11 연차", null).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AI_RATE_LIMITED"));
        clock.set(MONDAY.atTime(9, 1));
        draft(org.yoon, "3/11 연차", null).andExpect(status().isOk());
    }

    @Test
    void 문장이_비거나_너무_길면_VALIDATION_ERROR() throws Exception {
        draft(org.cho, " ", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        draft(org.cho, "가".repeat(501), null).andExpect(status().isBadRequest());
    }
}
