package com.nexuslabs.hr.domain.evaluation;

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

import java.util.Map;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EVAL-01 · 03 평가 기간 · 대상 규칙 · 미리보기 · 시작 · 마감 (API 설계서 12.1).
 * DemoOrg: 최상위(대표 ceo) → 개발본부(강하늘) → 프론트엔드팀(서예린 · 조현우) · 백엔드팀(윤). 인사 담당 hr · 회사 등록 관리자는 최상위 소속.
 * 평가자 = 소속 조직장, 본인이 조직장이면 상위 조직장(BR-EVAL-001). 최상위 조직장은 평가자가 없어 빠진다.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class EvalCycleTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    TestFixture.Employee hr;
    long general, leader;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        hr = fixture.employee(cid, org.company.rootOrgUnitId(), "인사 담당", false);
        general = template("일반 평가");
        leader = template("리더 평가");
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(hr, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long template(String name) throws Exception {
        String body = send(post("/api/eval-templates"), """
                {"name": "%s", "criteria": [{"category": "성과", "name": "성과", "weight": 100,
                  "questions": [{"content": "목표를 달성했는가"}]}]}""".formatted(name))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long cycle(String name, String start, String end) throws Exception {
        String body = send(post("/api/eval-cycles"), """
                {"name": "%s", "startDate": "%s", "endDate": "%s"}""".formatted(name, start, end))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 1순위 조직장 → 리더 평가, 2순위 인턴 → 제외, 3순위 나머지 → 일반 평가. */
    private ResultActions putRules(long cycleId, long internTypeId) throws Exception {
        return send(put("/api/eval-cycles/" + cycleId + "/targets"), """
                {"rules": [
                  {"priority": 1, "condIsOrgLead": true, "evalTemplateId": %d},
                  {"priority": 2, "condEmploymentTypeId": %d, "evalTemplateId": null},
                  {"priority": 3, "evalTemplateId": %d}]}""".formatted(leader, internTypeId, general));
    }

    private long internType() {
        return jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '인턴'",
                Long.class, cid);
    }

    @Test
    void 기간_만들기와_수정_겹치면_경고만() throws Exception {
        send(post("/api/eval-cycles"), """
                {"name": "상반기", "startDate": "2030-06-30", "endDate": "2030-06-01"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.endDate").exists());
        send(post("/api/eval-cycles"), """
                {"name": "상반기", "startDate": "2030-06-01", "endDate": "2030-06-30"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.evaluationCount").value(0))
                .andExpect(jsonPath("$.data.warning").value(nullValue()));
        send(post("/api/eval-cycles"), """
                {"name": "수습", "startDate": "2030-06-30", "endDate": "2030-07-15"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.warning").value("PERIOD_OVERLAP"));
        as(hr, get("/api/eval-cycles"))
                .andExpect(jsonPath("$.data[*].name").value(contains("수습", "상반기")));
    }

    @Test
    void 미리보기는_대상자_템플릿_평가자와_빠지는_이유를_보여_주고_시작하면_그대로_저장한다() throws Exception {
        long intern = internType();
        TestFixture.Employee newbie = fixture.employee(cid, org.frontendTeam, "직원", false);
        jdbc.update("UPDATE employee SET employment_type_id = ? WHERE id = ?", intern, newbie.id());
        TestFixture.Employee onLeave = fixture.employee(cid, org.backendTeam, "직원", false);
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", onLeave.id());
        long id = cycle("하반기", "2030-03-01", "2030-03-31");
        putRules(id, intern).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules[*].priority").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$.data.rules[0].evalTemplateName").value("리더 평가"))
                .andExpect(jsonPath("$.data.rules[1].condEmploymentTypeName").value("인턴"))
                .andExpect(jsonPath("$.data.rules[1].evalTemplateId").value(nullValue()));

        String target = "$.data.targets[?(@.employeeId == %d)].";
        String excluded = "$.data.excluded[?(@.employeeId == %d)].reason";
        as(hr, get("/api/eval-cycles/" + id + "/targets/preview"))
                .andExpect(status().isOk())
                // 팀원 → 팀장, 팀장 → 본부장, 본부장 → 대표, 최상위 소속 직원 → 대표
                .andExpect(jsonPath(target.formatted(org.cho.id()) + "evaluatorId").value(contains(Math.toIntExact(org.seo.id()))))
                .andExpect(jsonPath(target.formatted(org.cho.id()) + "evalTemplateName").value(contains("일반 평가")))
                .andExpect(jsonPath(target.formatted(org.seo.id()) + "evaluatorId").value(contains(Math.toIntExact(org.kang.id()))))
                .andExpect(jsonPath(target.formatted(org.seo.id()) + "evalTemplateName").value(contains("리더 평가")))
                .andExpect(jsonPath(target.formatted(org.yoon.id()) + "evaluatorId").value(contains(Math.toIntExact(org.kang.id()))))
                .andExpect(jsonPath(target.formatted(org.kang.id()) + "evaluatorId").value(contains(Math.toIntExact(org.ceo.id()))))
                .andExpect(jsonPath(target.formatted(hr.id()) + "evaluatorId").value(contains(Math.toIntExact(org.ceo.id()))))
                .andExpect(jsonPath(excluded.formatted(org.ceo.id())).value(contains("NO_EVALUATOR")))
                .andExpect(jsonPath(excluded.formatted(newbie.id())).value(contains("RULE_EXCLUDED")))
                .andExpect(jsonPath(excluded.formatted(onLeave.id())).value(contains("ON_LEAVE")))
                .andExpect(jsonPath("$.data.targets.length()").value(6));    // 관리자 · hr · 강하늘 · 서예린 · 조현우 · 윤

        as(hr, post("/api/eval-cycles/" + id + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.startedAt").exists())
                .andExpect(jsonPath("$.data.evaluationCount").value(6));
        Map<String, Object> cho = jdbc.queryForMap("""
                SELECT evaluator_id, eval_template_id, status::text AS status FROM evaluation
                WHERE eval_cycle_id = ? AND target_employee_id = ?""", id, org.cho.id());
        assertThat(((Number) cho.get("evaluator_id")).longValue()).isEqualTo(org.seo.id());
        assertThat(((Number) cho.get("eval_template_id")).longValue()).isEqualTo(general);
        assertThat(cho.get("status")).isEqualTo("NOT_STARTED");

        // 시작 뒤에는 규칙 · 이름을 못 바꾸고 종료일 연장만
        putRules(id, intern).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        as(hr, post("/api/eval-cycles/" + id + "/start")).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        send(patch("/api/eval-cycles/" + id), "{\"name\": \"바꾼 이름\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        send(patch("/api/eval-cycles/" + id), "{\"endDate\": \"2030-03-20\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.endDate").exists());
        send(patch("/api/eval-cycles/" + id), "{\"endDate\": \"2030-04-15\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").value("2030-04-15"));

        as(hr, post("/api/eval-cycles/" + id + "/close"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedAt").exists());
        as(hr, post("/api/eval-cycles/" + id + "/close")).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        send(patch("/api/eval-cycles/" + id), "{\"endDate\": \"2030-05-01\"}")
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 예정_기간은_모두_고칠_수_있고_규칙이_없거나_대상이_없으면_시작할_수_없다() throws Exception {
        long id = cycle("수습 평가", "2030-03-01", "2030-03-31");
        send(patch("/api/eval-cycles/" + id), "{\"name\": \"수습 평가 1차\", \"startDate\": \"2030-02-15\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수습 평가 1차"))
                .andExpect(jsonPath("$.data.startDate").value("2030-02-15"));
        send(patch("/api/eval-cycles/" + id), "{\"startDate\": \"3월\"}")
                .andExpect(jsonPath("$.error.fields.startDate").exists());

        as(hr, post("/api/eval-cycles/" + id + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EVAL_NO_TARGET"));
        // 모두 제외하는 규칙뿐이어도 대상 0명
        send(put("/api/eval-cycles/" + id + "/targets"), "{\"rules\": [{\"priority\": 1, \"evalTemplateId\": null}]}")
                .andExpect(status().isOk());
        as(hr, post("/api/eval-cycles/" + id + "/start")).andExpect(jsonPath("$.error.code").value("EVAL_NO_TARGET"));

        // 규칙 검사 — 우선순위 겹침, 비활성 템플릿
        send(put("/api/eval-cycles/" + id + "/targets"), """
                {"rules": [{"priority": 1, "evalTemplateId": %d}, {"priority": 1, "evalTemplateId": null}]}"""
                .formatted(general))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.rules").exists());
        jdbc.update("UPDATE eval_template SET is_active = FALSE WHERE id = ?", leader);
        send(put("/api/eval-cycles/" + id + "/targets"), "{\"rules\": [{\"priority\": 1, \"evalTemplateId\": %d}]}"
                .formatted(leader))
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        // 실패한 교체는 아무것도 바꾸지 않는다
        as(hr, get("/api/eval-cycles/" + id + "/targets"))
                .andExpect(jsonPath("$.data.rules.length()").value(1));

        // 권한 · 다른 회사
        as(org.seo, get("/api/eval-cycles")).andExpect(status().isForbidden());
        TestFixture.Company other = fixture.company("다른회사");
        long otherCycle = jdbc.queryForObject("""
                INSERT INTO eval_cycle (company_id, name, start_date, end_date) VALUES (?, '남의 기간', '2030-01-01', '2030-01-31')
                RETURNING id""", Long.class, other.id());
        as(hr, post("/api/eval-cycles/" + otherCycle + "/start")).andExpect(status().isNotFound());
    }
}
