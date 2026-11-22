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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 업무 분류 · 연장근무 · 출장 결재 초안(2026-11-19). 규칙 기반 FakeLlmClient(ai.client=fake)만 쓴다 — 실제 Claude API 를 부르지 않는다.
 * 시계는 2030-03-04(월) 09:00. 조현우의 연장근무 · 출장 승인자는 서예린(조직장 1단계), 휴가는 서예린 → 강하늘.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class AiOvertimeTripDraftTest {

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
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions send(TestFixture.Employee e, String path, String message, Long draftId) throws Exception {
        String body = draftId == null
                ? "{\"message\": \"%s\"}".formatted(message)
                : "{\"message\": \"%s\", \"draftId\": %d}".formatted(message, draftId);
        return as(e, post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions overtime(TestFixture.Employee e, String message, Long draftId) throws Exception {
        return send(e, "/api/me/ai/overtime-drafts", message, draftId);
    }

    private ResultActions trip(TestFixture.Employee e, String message, Long draftId) throws Exception {
        return send(e, "/api/me/ai/trip-drafts", message, draftId);
    }

    private static long draftId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.draftId"))
                .longValue();
    }

    private String name(TestFixture.Employee e) {
        return jdbc.queryForObject("SELECT name FROM employee WHERE id = ?", String.class, e.id());
    }

    private String rowStatus(String table, long id) {
        return jdbc.queryForObject("SELECT status::text FROM " + table + " WHERE id = ?", String.class, id);
    }

    // ---------------------------------------------------------------- 분류

    @Test
    void 자유_입력은_키워드로_업무를_고른다() throws Exception {
        String[][] cases = {
                {"내일 7시부터 10시까지 야근해요", "OVERTIME"},
                {"다음 주에 부산 출장 가요", "BUSINESS_TRIP"},
                {"금요일에 연차 쓸게요", "LEAVE"},
                {"점심 메뉴 추천해 줘", "UNKNOWN"},
        };
        for (String[] c : cases) {
            send(org.cho, "/api/me/ai/route", c[0], null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.workType").value(c[1]));
        }
    }

    @Test
    void 분류와_초안은_직원당_분당_10번을_함께_센다() throws Exception {
        for (int i = 0; i < 9; i++) {
            send(org.yoon, "/api/me/ai/route", "연차 쓸게요", null).andExpect(status().isOk());
        }
        overtime(org.yoon, "3/5 19:00~22:00 배포 작업 때문에 연장근무", null).andExpect(status().isOk());
        send(org.yoon, "/api/me/ai/route", "연차 쓸게요", null).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AI_RATE_LIMITED"));
    }

    // ---------------------------------------------------------------- 연장근무

    @Test
    void 연장근무_날짜_시각_사유가_다_있으면_READY_미리보기까지() throws Exception {
        overtime(org.cho, "3/5 19:00~22:00 배포 작업 때문에 연장근무", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.question").doesNotExist())
                .andExpect(jsonPath("$.data.draft.workDate").value("2030-03-05"))
                .andExpect(jsonPath("$.data.draft.plannedStart").value("19:00"))
                .andExpect(jsonPath("$.data.draft.plannedEnd").value("22:00"))
                .andExpect(jsonPath("$.data.draft.reason").value("배포 작업"))
                .andExpect(jsonPath("$.data.preview.minutes").value(180))
                .andExpect(jsonPath("$.data.preview.approvalSteps[0].approverName").value(name(org.seo)))
                .andExpect(jsonPath("$.data.errors").isEmpty());
        // 오전 · 오후 표시가 없는 1–11시는 오후, 끝이 시작보다 이르면 다음 날
        overtime(org.cho, "3/6 7시부터 10시까지 장애 대응 때문에 야근", null)
                .andExpect(jsonPath("$.data.draft.plannedStart").value("19:00"))
                .andExpect(jsonPath("$.data.draft.plannedEnd").value("22:00"));
        overtime(org.cho, "3/7 22:00~01:00 점검 때문에 연장근무", null)
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.preview.minutes").value(180));
        // 초안만 — 신청은 아직 없다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM overtime_request WHERE company_id = ?", Long.class, cid))
                .isZero();
    }

    @Test
    void 연장근무_시각이_없으면_되묻고_답하면_READY() throws Exception {
        long id = draftId(overtime(org.cho, "3/5 배포 작업 때문에 연장근무", null)
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"))
                .andExpect(jsonPath("$.data.question").value("몇 시부터 연장근무를 하시나요? 예) 19:00"))
                .andExpect(jsonPath("$.data.preview").doesNotExist()));
        overtime(org.cho, "7시부터 10시까지요", id)
                .andExpect(jsonPath("$.data.draftId").value(id))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.workDate").value("2030-03-05"))
                .andExpect(jsonPath("$.data.draft.reason").value("배포 작업"));
        // 사유가 빠지면 사유를 묻는다
        overtime(org.cho, "3/6 19:00~21:00 연장근무", null)
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"))
                .andExpect(jsonPath("$.data.question").value("어떤 일로 연장근무를 하시나요? 사유를 알려 주세요."));
    }

    @Test
    void 연장근무_휴가일_같은날_중복_마감월이면_INVALID() throws Exception {
        // 3/6 승인된 휴가(근태 ON_VACATION)
        approvedLeave(org.cho, "2030-03-06");
        overtime(org.cho, "3/6 19:00~22:00 배포 작업 때문에 연장근무", null)
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andExpect(jsonPath("$.data.errors", hasItem("ATT_ON_LEAVE_OR_TRIP")));

        // 같은 날 진행 중 신청
        as(org.cho, post("/api/me/overtime-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"workDate": "2030-03-05", "plannedStart": "18:00", "plannedEnd": "20:00", "reason": "점검"}"""))
                .andExpect(status().isCreated());
        overtime(org.cho, "3/5 20:00~22:00 배포 작업 때문에 연장근무", null)
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andExpect(jsonPath("$.data.errors", hasItem("OVERTIME_DUPLICATE")));

        // 정산 마감된 달
        jdbc.update("""
                        INSERT INTO payroll_run (company_id, pay_month, pay_date, company_name_snap, ceo_name_snap,
                                                 business_reg_no_snap, company_address_snap, confirmed_by)
                        VALUES (?, '2030-02', DATE '2030-03-10', '데모랩스', '대표', '000-00-00000', '서울', ?)
                        """,
                cid, org.company.adminId());
        overtime(org.cho, "2/27 19:00~22:00 결산 때문에 연장근무", null)
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andExpect(jsonPath("$.data.errors", hasItem("PAY_MONTH_SETTLED")));
    }

    @Test
    void 연장근무_확인하면_신청이_생기고_승인자에게_알림() throws Exception {
        long id = draftId(overtime(org.cho, "3/5 19:00~22:00 배포 작업 때문에 연장근무", null));
        String body = as(org.cho, post("/api/me/ai/overtime-drafts/" + id + "/confirm"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeId").value(org.cho.id()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestedMinutes").value(180))
                .andExpect(jsonPath("$.data.reason").value("배포 작업"))
                .andReturn().getResponse().getContentAsString();
        long overtimeId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(rowStatus("ai_overtime_draft", id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT overtime_request_id FROM ai_overtime_draft WHERE id = ?", Long.class, id))
                .isEqualTo(overtimeId);
        assertThat(jdbc.queryForObject("""
                        SELECT body FROM notification WHERE company_id = ? AND recipient_id = ? AND work_type = 'OVERTIME'
                          AND target_id = ?""",
                String.class, cid, org.seo.id(), overtimeId))
                .isEqualTo(name(org.cho) + " · 연장근무 · 3/5(화) 19:00~22:00 (3시간)");
        // 같은 초안으로 두 번 신청할 수 없다
        as(org.cho, post("/api/me/ai/overtime-drafts/" + id + "/confirm")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 연장근무_남의_초안_만료된_초안은_거부() throws Exception {
        long id = draftId(overtime(org.cho, "3/5 19:00~22:00 배포 작업 때문에 연장근무", null));
        as(org.seo, post("/api/me/ai/overtime-drafts/" + id + "/confirm")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AI_DRAFT_NOT_FOUND"));
        overtime(org.seo, "3/6로 바꿔줘", id).andExpect(status().isNotFound());
        as(org.seo, post("/api/me/ai/overtime-drafts/" + id + "/discard")).andExpect(status().isNotFound());

        clock.set(MONDAY.atTime(9, 31));
        as(org.cho, post("/api/me/ai/overtime-drafts/" + id + "/confirm")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AI_DRAFT_EXPIRED"));
        assertThat(rowStatus("ai_overtime_draft", id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM overtime_request WHERE company_id = ?", Long.class, cid))
                .isZero();
    }

    // ---------------------------------------------------------------- 출장

    @Test
    void 출장_목적지_기간_목적이_있으면_비용을_안_말해도_READY() throws Exception {
        trip(org.cho, "3/11~3/12 부산 출장, 거래처 미팅", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.tripType").value("DOMESTIC"))
                .andExpect(jsonPath("$.data.draft.destination").value("부산"))
                .andExpect(jsonPath("$.data.draft.purpose").value("거래처 미팅"))
                .andExpect(jsonPath("$.data.draft.startDate").value("2030-03-11"))
                .andExpect(jsonPath("$.data.draft.endDate").value("2030-03-12"))
                .andExpect(jsonPath("$.data.draft.estimatedCost").doesNotExist())
                .andExpect(jsonPath("$.data.preview.days").value(2))
                .andExpect(jsonPath("$.data.preview.approvalSteps[0].approverName").value(name(org.seo)))
                .andExpect(jsonPath("$.data.errors").isEmpty());
        // 비용을 말하면 채운다, 해외 도시면 해외 출장
        trip(org.cho, "3/18 도쿄 출장, 전시회 참관, 비용 150만 원", null)
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.tripType").value("OVERSEAS"))
                .andExpect(jsonPath("$.data.draft.estimatedCost").value(1_500_000));
    }

    @Test
    void 출장_목적지가_없으면_되묻고_답하면_READY() throws Exception {
        long id = draftId(trip(org.cho, "3/11 거래처 미팅 때문에 출장", null)
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"))
                .andExpect(jsonPath("$.data.question").value("어디로 출장 가시나요?"))
                .andExpect(jsonPath("$.data.draft.purpose").value("거래처 미팅")));
        trip(org.cho, "부산 출장이에요", id)
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.destination").value("부산"))
                .andExpect(jsonPath("$.data.draft.startDate").value("2030-03-11"))
                .andExpect(jsonPath("$.data.draft.endDate").value("2030-03-11"));
    }

    @Test
    void 출장_시작일만_바꾸면_기존_종료일은_유지하고_늦어지면_되묻는다() throws Exception {
        long id = draftId(trip(org.cho, "3/20~3/22 부산 출장, 거래처 미팅", null)
                .andExpect(jsonPath("$.data.status").value("READY")));
        trip(org.cho, "3/21부터로 바꿔줘", id)
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.startDate").value("2030-03-21"))
                .andExpect(jsonPath("$.data.draft.endDate").value("2030-03-22"))
                .andExpect(jsonPath("$.data.preview.days").value(2));
        // 새 시작일이 기존 종료일보다 늦으면 하루짜리로 덮지 않고 되묻는다
        trip(org.cho, "3/23부터로 바꿔줘", id)
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"))
                .andExpect(jsonPath("$.data.question").value("끝나는 날이 시작일보다 빠릅니다. 날짜를 다시 알려 주세요."));
    }

    @Test
    void 출장_휴가와_기간이_겹치면_INVALID() throws Exception {
        long annual = annualType();
        grant(org.cho, annual);
        as(org.cho, post("/api/me/leave-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"leaveTypeId": %d, "startDate": "2030-03-12", "endDate": "2030-03-12"}""".formatted(annual)))
                .andExpect(status().isCreated());
        trip(org.cho, "3/11~3/12 부산 출장, 거래처 미팅", null)
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andExpect(jsonPath("$.data.errors", hasItem("PERIOD_OVERLAP")));
    }

    @Test
    void 출장_확인하면_신청이_생기고_승인자_알림에_목적지_기간() throws Exception {
        long id = draftId(trip(org.cho, "3/11~3/12 부산 출장, 거래처 미팅", null));
        String body = as(org.cho, post("/api/me/ai/trip-drafts/" + id + "/confirm"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.destination").value("부산"))
                .andExpect(jsonPath("$.data.tripType").value("DOMESTIC"))
                .andReturn().getResponse().getContentAsString();
        long tripId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(rowStatus("ai_trip_draft", id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT business_trip_id FROM ai_trip_draft WHERE id = ?", Long.class, id))
                .isEqualTo(tripId);
        assertThat(jdbc.queryForObject("""
                        SELECT body FROM notification WHERE company_id = ? AND recipient_id = ?
                          AND work_type = 'BUSINESS_TRIP' AND target_id = ?""",
                String.class, cid, org.seo.id(), tripId))
                .isEqualTo(name(org.cho) + " · 출장 · 부산 · 3/11(월)~3/12(화)");
    }

    @Test
    void 출장_남의_초안_만료된_초안은_거부() throws Exception {
        long id = draftId(trip(org.cho, "3/11~3/12 부산 출장, 거래처 미팅", null));
        as(org.seo, post("/api/me/ai/trip-drafts/" + id + "/confirm")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AI_DRAFT_NOT_FOUND"));
        clock.set(MONDAY.atTime(9, 31));
        as(org.cho, post("/api/me/ai/trip-drafts/" + id + "/confirm")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AI_DRAFT_EXPIRED"));
        assertThat(rowStatus("ai_trip_draft", id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_trip WHERE company_id = ?", Long.class, cid))
                .isZero();
    }

    // ---------------------------------------------------------------- 준비

    private long annualType() {
        return jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'", Long.class, cid);
    }

    private void grant(TestFixture.Employee e, long typeId) {
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2030, 'REGULAR', 10)""", cid, e.id(), typeId);
    }

    /** 휴가를 신청하고 승인선(서예린 → 강하늘)을 끝까지 승인한다 → 그날 근태가 ON_VACATION. */
    private void approvedLeave(TestFixture.Employee e, String date) throws Exception {
        long annual = annualType();
        grant(e, annual);
        String body = as(e, post("/api/me/leave-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"leaveTypeId": %d, "startDate": "%s", "endDate": "%s"}""".formatted(annual, date, date)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long leaveId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        for (TestFixture.Employee approver : new TestFixture.Employee[]{org.seo, org.kang}) {
            long stepId = jdbc.queryForObject("""
                    SELECT id FROM approval_step WHERE company_id = ? AND work_type = 'LEAVE' AND target_id = ?
                      AND status = 'PENDING'""", Long.class, cid, leaveId);
            as(approver, post("/api/approvals/" + stepId + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("""
                SELECT status::text FROM attendance WHERE company_id = ? AND employee_id = ? AND work_date = ?::date""",
                String.class, cid, e.id(), date)).isEqualTo("ON_VACATION");
    }
}
