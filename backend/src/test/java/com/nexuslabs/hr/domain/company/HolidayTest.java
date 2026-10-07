package com.nexuslabs.hr.domain.company;

import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-COMP-05 휴일 관리 (API 설계서 3장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HolidayTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("휴일테스트");
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions create(String date, String name, String type, boolean recurring) throws Exception {
        return asAdmin(json(post("/api/holidays"), """
                {"holidayDate": "%s", "name": "%s", "holidayType": "%s", "isRecurring": %s}
                """.formatted(date, name, type, recurring)));
    }

    private long holidayId(String date) {
        return jdbc.queryForObject("SELECT id FROM holiday WHERE company_id = ? AND holiday_date = ?", Long.class,
                company.id(), Date.valueOf(date));
    }

    private String staffToken() {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        return fixture.token(staff.id(), company.id());
    }

    // ---------------------------------------------------------------- 등록 · 조회

    @Test
    void 등록한_휴일은_그_해_목록에_날짜순으로_나온다() throws Exception {
        create("2026-10-09", "한글날", "PUBLIC", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.holidayDate").value("2026-10-09"))
                .andExpect(jsonPath("$.data.name").value("한글날"))
                .andExpect(jsonPath("$.data.holidayType").value("PUBLIC"))
                .andExpect(jsonPath("$.data.isRecurring").value(false));
        asAdmin(json(post("/api/holidays"), """
                {"holidayDate": "2026-03-02", "name": " 대체공휴일 ", "holidayType": "SUBSTITUTE"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("대체공휴일"))
                .andExpect(jsonPath("$.data.isRecurring").value(false));

        // 조회는 로그인만 하면 된다
        mvc.perform(get("/api/holidays?year=2026").header("Authorization", staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].holidayDate").value(contains("2026-03-02", "2026-10-09")));
        asAdmin(get("/api/holidays?year=2027")).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 매년_반복_휴일은_등록한_해부터_해마다_그_해_날짜로_나온다() throws Exception {
        create("2026-04-15", "창립기념일", "COMPANY", true).andExpect(status().isCreated());
        long id = holidayId("2026-04-15");

        asAdmin(get("/api/holidays?year=2026"))
                .andExpect(jsonPath("$.data[0].holidayDate").value("2026-04-15"))
                .andExpect(jsonPath("$.data[0].isRecurring").value(true));
        asAdmin(get("/api/holidays?year=2030"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].holidayDate").value("2030-04-15"))
                .andExpect(jsonPath("$.data[0].name").value("창립기념일"));
        // 등록한 해보다 이전에는 적용하지 않는다
        asAdmin(get("/api/holidays?year=2025")).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 이월_29일_반복_휴일은_윤년에만_나온다() throws Exception {
        create("2028-02-29", "윤일 휴무", "COMPANY", true).andExpect(status().isCreated());

        asAdmin(get("/api/holidays?year=2029")).andExpect(jsonPath("$.data.length()").value(0));
        asAdmin(get("/api/holidays?year=2032"))
                .andExpect(jsonPath("$.data[0].holidayDate").value("2032-02-29"));
    }

    @Test
    void 같은_날짜는_두_번_휴일이_될_수_없다() throws Exception {
        create("2026-10-09", "한글날", "PUBLIC", false).andExpect(status().isCreated());
        create("2026-04-15", "창립기념일", "COMPANY", true).andExpect(status().isCreated());

        create("2026-10-09", "또 한글날", "COMPANY", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        // 반복 휴일과 같은 월·일(등록한 해 이후)
        create("2027-04-15", "임시 휴무", "COMPANY", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        // 새 반복 휴일이 이미 있는 그해 휴일과 겹침
        create("2025-10-09", "매년 한글날", "PUBLIC", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        // 반복 휴일이 시작되기 전 해의 같은 월·일은 겹치지 않는다
        create("2025-04-15", "작년 임시 휴무", "COMPANY", false).andExpect(status().isCreated());
    }

    @Test
    void 등록_거부_필수값_잘못된_종류() throws Exception {
        asAdmin(json(post("/api/holidays"), "{\"name\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.holidayDate").exists())
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.holidayType").exists());
        create("2026-05-01", "근로자의 날", "NATIONAL", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
    }

    // ---------------------------------------------------------------- 수정 · 삭제

    @Test
    void 수정은_보낸_필드만_바꾼다() throws Exception {
        create("2026-06-03", "지방선거", "PUBLIC", false).andExpect(status().isCreated());
        long id = holidayId("2026-06-03");

        asAdmin(json(patch("/api/holidays/" + id), "{\"name\": \"전국동시지방선거\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("전국동시지방선거"))
                .andExpect(jsonPath("$.data.holidayDate").value("2026-06-03"))
                .andExpect(jsonPath("$.data.holidayType").value("PUBLIC"))
                .andExpect(jsonPath("$.data.isRecurring").value(false));
        asAdmin(json(patch("/api/holidays/" + id), """
                {"holidayDate": "2026-06-04", "holidayType": "COMPANY", "isRecurring": true}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holidayDate").value("2026-06-04"))
                .andExpect(jsonPath("$.data.holidayType").value("COMPANY"))
                .andExpect(jsonPath("$.data.isRecurring").value(true))
                .andExpect(jsonPath("$.data.name").value("전국동시지방선거"));
    }

    @Test
    void 수정_거부_겹치는_날짜_비울_수_없는_값_잘못된_값() throws Exception {
        create("2026-10-03", "개천절", "PUBLIC", false).andExpect(status().isCreated());
        create("2026-10-09", "한글날", "PUBLIC", false).andExpect(status().isCreated());
        long id = holidayId("2026-10-09");

        asAdmin(json(patch("/api/holidays/" + id), "{\"holidayDate\": \"2026-10-03\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        // 자기 자신과는 겹치는 것으로 보지 않는다
        asAdmin(json(patch("/api/holidays/" + id), "{\"holidayDate\": \"2026-10-09\", \"isRecurring\": true}"))
                .andExpect(status().isOk());
        asAdmin(json(patch("/api/holidays/" + id), "{\"name\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.name").exists());
        asAdmin(json(patch("/api/holidays/" + id), "{\"holidayDate\": \"시월 구일\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.holidayDate").exists());
        asAdmin(json(patch("/api/holidays/" + id), "{\"holidayType\": \"NATIONAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
        asAdmin(json(patch("/api/holidays/" + id), "{\"memo\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.memo").exists());
    }

    @Test
    void 삭제하면_목록에서_사라진다() throws Exception {
        create("2026-12-25", "성탄절", "PUBLIC", true).andExpect(status().isCreated());
        long id = holidayId("2026-12-25");

        asAdmin(delete("/api/holidays/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        asAdmin(get("/api/holidays?year=2027")).andExpect(jsonPath("$.data.length()").value(0));
        asAdmin(delete("/api/holidays/" + id)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 감사 로그 · 권한 · 회사 격리

    @Test
    void 등록_수정_삭제는_감사_로그로_남는다() throws Exception {
        create("2026-08-17", "임시공휴일", "PUBLIC", false).andExpect(status().isCreated());
        long id = holidayId("2026-08-17");
        asAdmin(json(patch("/api/holidays/" + id), "{\"name\": \"대체공휴일\"}")).andExpect(status().isOk());
        asAdmin(delete("/api/holidays/" + id)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
                SELECT action::text FROM audit_log
                WHERE company_id = ? AND target_type = ? AND target_id = ? AND actor_id = ? ORDER BY id
                """, String.class, company.id(), "HOLIDAY", id, company.adminId()))
                .containsExactly("CREATE", "UPDATE", "DELETE");
    }

    @Test
    void 등록_수정_삭제는_COMPANY_MANAGE_가_있어야_한다() throws Exception {
        create("2026-09-24", "추석 연휴", "PUBLIC", false).andExpect(status().isCreated());
        long id = holidayId("2026-09-24");
        String staffToken = staffToken();

        mvc.perform(json(post("/api/holidays"), "{}").header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(json(patch("/api/holidays/" + id), "{\"name\": \"x\"}").header("Authorization", staffToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/holidays/" + id).header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_회사의_휴일은_보이지_않고_수정_삭제는_404() throws Exception {
        create("2026-10-09", "한글날", "PUBLIC", false).andExpect(status().isCreated());
        long id = holidayId("2026-10-09");
        TestFixture.Company other = fixture.company("휴일테스트타사");
        String otherToken = fixture.token(other.adminId(), other.id());

        mvc.perform(get("/api/holidays?year=2026").header("Authorization", otherToken))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(json(patch("/api/holidays/" + id), "{\"name\": \"남의 휴일\"}").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/holidays/" + id).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        // 다른 회사는 같은 날짜를 휴일로 등록할 수 있다
        mvc.perform(json(post("/api/holidays"), """
                        {"holidayDate": "2026-10-09", "name": "한글날", "holidayType": "PUBLIC"}
                        """).header("Authorization", otherToken))
                .andExpect(status().isCreated());
        asAdmin(get("/api/holidays?year=2026")).andExpect(jsonPath("$.data[0].name").value("한글날"));
    }
}
