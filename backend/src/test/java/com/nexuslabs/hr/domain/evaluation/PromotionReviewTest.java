package com.nexuslabs.hr.domain.evaluation;

import com.jayway.jsonpath.JsonPath;
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

import java.sql.Date;
import java.time.LocalDate;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 승진 고과(2026-11-04). 심사 기간은 회사 공통이고, 기간 시작일에 지금 직급에 N년 이상 있는 직원만 대상이다.
 * 결과는 본인에게 공개하지 않고 전사 조회 권한자만 본다. 시계는 2030-03-04(월).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class PromotionReviewTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    long cid;
    long hr, exec, lead, veteran, junior, top;
    long template, question;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.atTime(9, 0));
        TestFixture.Company company = fixture.company("승진고과테스트");
        cid = company.id();
        long team = fixture.orgUnit(cid, company.rootOrgUnitId(), "개발팀");
        hr = fixture.employee(cid, company.rootOrgUnitId(), "인사 담당", false).id();
        exec = fixture.employee(cid, company.rootOrgUnitId(), "경영진", false).id();
        lead = fixture.employee(cid, team, "직원", false).id();
        fixture.lead(cid, team, lead);
        veteran = staff(team, "2025-01-01", "사원");
        junior = staff(team, "2029-01-01", "사원");
        top = staff(team, "2010-01-01", "부장");
        String body = send(hr, post("/api/eval-templates"), """
                {"name": "승진 고과", "criteria": [{"category": "성과", "name": "성과 실적", "weight": 100,
                  "questions": [{"content": "실적이 충분한가"}]}]}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        template = id(body, "$.data.id");
        question = id(body, "$.data.criteria[0].questions[0].id");
    }

    private long staff(long team, String hireDate, String grade) {
        long id = fixture.employee(cid, team, "직원", false).id();
        jdbc.update("""
                UPDATE employee SET hire_date = ?, job_grade_id = (SELECT id FROM job_grade WHERE company_id = ? AND name = ?)
                WHERE id = ?""", Date.valueOf(LocalDate.parse(hireDate)), cid, grade, id);
        return id;
    }

    private static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, cid)));
    }

    private ResultActions send(long employeeId, MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(employeeId, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long promotionCycle(String name, String start, String end, int years) throws Exception {
        String body = send(hr, post("/api/eval-cycles"), """
                {"name": "%s", "startDate": "%s", "endDate": "%s", "kind": "PROMOTION", "minYearsInGrade": %d}"""
                .formatted(name, start, end, years))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cycle = id(body, "$.data.id");
        send(hr, put("/api/eval-cycles/" + cycle + "/targets"),
                "{\"rules\": [{\"priority\": 1, \"evalTemplateId\": " + template + "}]}").andExpect(status().isOk());
        return cycle;
    }

    /** 기간을 시작하고 대상자의 평가를 평가자가 제출한 뒤 인사 담당이 확정한다. */
    private long confirmed(long cycle, int score) throws Exception {
        as(hr, post("/api/eval-cycles/" + cycle + "/start")).andExpect(status().isOk());
        long evaluation = jdbc.queryForObject(
                "SELECT id FROM evaluation WHERE eval_cycle_id = ? AND target_employee_id = ?", Long.class, cycle, veteran);
        send(lead, put("/api/evaluations/" + evaluation + "/answers"), """
                {"answers": [{"questionId": %d, "score": %d}], "overallComment": "의견"}""".formatted(question, score))
                .andExpect(status().isOk());
        as(lead, post("/api/evaluations/" + evaluation + "/submit")).andExpect(status().isOk());
        as(hr, post("/api/evaluations/" + evaluation + "/confirm")).andExpect(status().isOk());
        return evaluation;
    }

    @Test
    void 유형을_고르지_않으면_정기_평가이고_고과는_체류_연수가_필수다() throws Exception {
        send(hr, post("/api/eval-cycles"), """
                {"name": "정기", "startDate": "2030-03-01", "endDate": "2030-03-31", "minYearsInGrade": 3}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("REGULAR"))
                .andExpect(jsonPath("$.data.minYearsInGrade").value(nullValue()));
        send(hr, post("/api/eval-cycles"), """
                {"name": "고과", "startDate": "2030-03-01", "endDate": "2030-03-31", "kind": "PROMOTION"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.minYearsInGrade").exists());
        send(hr, post("/api/eval-cycles"), """
                {"name": "고과", "startDate": "2030-03-01", "endDate": "2030-03-31", "kind": "PROMOTION", "minYearsInGrade": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.minYearsInGrade").exists());
        as(hr, get("/api/eval-cycles")).andExpect(jsonPath("$.data[0].kind").value("REGULAR"));
    }

    @Test
    void 유형과_체류_연수는_만든_뒤_바꿀_수_없다() throws Exception {
        long cycle = promotionCycle("고과", "2030-03-01", "2030-03-31", 3);
        send(hr, patch("/api/eval-cycles/" + cycle), "{\"kind\": \"REGULAR\"}").andExpect(status().isBadRequest());
        send(hr, patch("/api/eval-cycles/" + cycle), "{\"minYearsInGrade\": 1}").andExpect(status().isBadRequest());
    }

    @Test
    void 고과는_기간_시작일에_체류_연수를_채운_직원만_대상이고_최고_직급은_뺀다() throws Exception {
        long cycle = promotionCycle("고과", "2030-03-01", "2030-03-31", 3);
        as(hr, get("/api/eval-cycles/" + cycle + "/targets/preview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targets.length()").value(1))
                .andExpect(jsonPath("$.data.targets[0].employeeId").value(veteran))
                .andExpect(jsonPath("$.data.excluded[?(@.employeeId == %d)].reason".formatted(junior)).value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.data.excluded[?(@.employeeId == %d)].reason".formatted(top)).value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.data.excluded[?(@.employeeId == %d)].reason".formatted(lead)).value("NOT_ELIGIBLE"));
        as(hr, post("/api/eval-cycles/" + cycle + "/start")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationCount").value(1));
    }

    @Test
    void 체류_연수는_기간_시작일로_판단한다() throws Exception {
        // 입사 2025-01-01 — 2028-01-01 에 3년을 채운다
        long before = promotionCycle("이른 고과", "2027-12-31", "2028-01-31", 3);
        as(hr, get("/api/eval-cycles/" + before + "/targets/preview"))
                .andExpect(jsonPath("$.data.targets.length()").value(0));
        long on = promotionCycle("맞는 고과", "2028-01-01", "2028-01-31", 3);
        as(hr, get("/api/eval-cycles/" + on + "/targets/preview"))
                .andExpect(jsonPath("$.data.targets.length()").value(1));
    }

    @Test
    void 정기_평가는_체류_연수와_상관없이_대상이다() throws Exception {
        String body = send(hr, post("/api/eval-cycles"), """
                {"name": "정기", "startDate": "2030-03-01", "endDate": "2030-03-31"}""")
                .andReturn().getResponse().getContentAsString();
        long cycle = id(body, "$.data.id");
        send(hr, put("/api/eval-cycles/" + cycle + "/targets"),
                "{\"rules\": [{\"priority\": 1, \"evalTemplateId\": " + template + "}]}").andExpect(status().isOk());
        as(hr, get("/api/eval-cycles/" + cycle + "/targets/preview"))
                .andExpect(jsonPath("$.data.targets[*].employeeId").value(hasItem((int) junior)))
                .andExpect(jsonPath("$.data.targets[*].employeeId").value(hasItem((int) top)));
    }

    @Test
    void 기간_겹침_경고는_같은_유형끼리만_본다() throws Exception {
        send(hr, post("/api/eval-cycles"), """
                {"name": "정기", "startDate": "2030-03-01", "endDate": "2030-03-31"}""")
                .andExpect(jsonPath("$.data.warning").value(nullValue()));
        send(hr, post("/api/eval-cycles"), """
                {"name": "고과1", "startDate": "2030-03-01", "endDate": "2030-03-31", "kind": "PROMOTION", "minYearsInGrade": 3}""")
                .andExpect(jsonPath("$.data.warning").value(nullValue()));
        send(hr, post("/api/eval-cycles"), """
                {"name": "고과2", "startDate": "2030-03-10", "endDate": "2030-03-20", "kind": "PROMOTION", "minYearsInGrade": 3}""")
                .andExpect(jsonPath("$.data.warning").value("PERIOD_OVERLAP"));
    }

    @Test
    void 확정해도_본인에게_보이지_않는다() throws Exception {
        long evaluation = confirmed(promotionCycle("고과", "2030-03-01", "2030-03-31", 3), 4);
        as(veteran, get("/api/me/evaluations")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        as(veteran, get("/api/evaluations/" + evaluation)).andExpect(status().isForbidden());
        as(veteran, get("/api/dashboard/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.me.latestEvaluation").value(nullValue()));
    }

    @Test
    void 전사_범위만_결과를_보고_팀_범위_조직장은_못_본다() throws Exception {
        long cycle = promotionCycle("고과", "2030-03-01", "2030-03-31", 3);
        long evaluation = confirmed(cycle, 4);
        as(hr, get("/api/evaluations/results?cycleId=" + cycle)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].evaluationId").value(evaluation))
                .andExpect(jsonPath("$.data.content[0].cycleKind").value("PROMOTION"))
                .andExpect(jsonPath("$.data.content[0].totalScore").value(80.0));
        as(exec, get("/api/evaluations/results")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].evaluationId").value(hasItem((int) evaluation)));
        as(lead, get("/api/evaluations/results?cycleId=" + cycle)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].evaluationId").value(not(hasItem((int) evaluation))));
        as(hr, get("/api/evaluations/results?kind=REGULAR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
        as(hr, get("/api/evaluations/results?kind=PROMOTION")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void 승진_대상자_평균에는_고과를_넣지_않고_고과_점수를_따로_준다() throws Exception {
        long regular = id(send(hr, post("/api/eval-cycles"), """
                {"name": "정기", "startDate": "2030-01-01", "endDate": "2030-01-31"}""")
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        send(hr, put("/api/eval-cycles/" + regular + "/targets"),
                "{\"rules\": [{\"priority\": 1, \"evalTemplateId\": " + template + "}]}").andExpect(status().isOk());
        confirmed(regular, 3);
        confirmed(promotionCycle("2030 승진 고과", "2030-03-01", "2030-03-31", 3), 5);
        String row = "$.data.content[?(@.employeeId == %d)]".formatted(veteran);
        as(hr, get("/api/promotions/candidates?minYears=3")).andExpect(status().isOk())
                .andExpect(jsonPath(row + ".averageScore").value(60.0))
                .andExpect(jsonPath(row + ".evaluationCount").value(1))
                .andExpect(jsonPath(row + ".promotionScore").value(100.0))
                .andExpect(jsonPath(row + ".promotionCycleName").value("2030 승진 고과"));
    }

    @Test
    void 승진_대상자에서_최고_직급은_뺀다() throws Exception {
        String res = as(hr, get("/api/promotions/candidates?minYears=3")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<Integer>>read(res, "$.data.content[*].employeeId"))
                .contains((int) veteran).doesNotContain((int) top, (int) junior);
    }
}
