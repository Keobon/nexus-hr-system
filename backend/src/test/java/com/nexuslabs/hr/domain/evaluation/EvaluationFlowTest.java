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

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EVAL-04 · 05 · 06 평가 입력 · 제출 · 확정 · 재오픈 · 결과 (API 설계서 12.2).
 * 템플릿: 성과(배점 60, 질문 2) · 태도(배점 40, 질문 1). 모두 같은 템플릿으로 시작한다.
 * DemoOrg 평가자: 조현우 → 서예린(프론트엔드팀장), 윤 → 강하늘(개발본부장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class EvaluationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    TestFixture.Employee hr;
    long cycle;
    long q1, q2, q3;          // 성과 질문 2개, 태도 질문 1개
    long choEval, yoonEval;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        hr = fixture.employee(cid, org.company.rootOrgUnitId(), "인사 담당", false);
        String template = send(post("/api/eval-templates"), """
                {"name": "일반 평가", "criteria": [
                  {"category": "성과", "name": "업무 성과", "weight": 60,
                   "questions": [{"content": "목표를 달성했는가"}, {"content": "품질이 좋은가"}]},
                  {"category": "태도", "name": "협업", "weight": 40, "questions": [{"content": "협력하는가"}]}]}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long templateId = id(template, "$.data.id");
        q1 = id(template, "$.data.criteria[0].questions[0].id");
        q2 = id(template, "$.data.criteria[0].questions[1].id");
        q3 = id(template, "$.data.criteria[1].questions[0].id");
        cycle = id(send(post("/api/eval-cycles"), """
                {"name": "상반기", "startDate": "2030-03-01", "endDate": "2030-03-31"}""")
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        send(put("/api/eval-cycles/" + cycle + "/targets"),
                "{\"rules\": [{\"priority\": 1, \"evalTemplateId\": " + templateId + "}]}").andExpect(status().isOk());
        as(hr, post("/api/eval-cycles/" + cycle + "/start")).andExpect(status().isOk());
        choEval = evaluationOf(org.cho);
        yoonEval = evaluationOf(org.yoon);
    }

    private static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private long evaluationOf(TestFixture.Employee target) {
        return jdbc.queryForObject("SELECT id FROM evaluation WHERE eval_cycle_id = ? AND target_employee_id = ?",
                Long.class, cycle, target.id());
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return send(hr, request, body);
    }

    private ResultActions send(TestFixture.Employee e, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return as(e, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions answer(TestFixture.Employee evaluator, long evaluationId, String body) throws Exception {
        return send(evaluator, put("/api/evaluations/" + evaluationId + "/answers"), body);
    }

    private String full(int s1, int s2, int s3, String comment) {
        return """
                {"answers": [{"questionId": %d, "score": %d}, {"questionId": %d, "score": %d},
                             {"questionId": %d, "score": %d}], "overallComment": "%s"}"""
                .formatted(q1, s1, q2, s2, q3, s3, comment);
    }

    @Test
    void 임시저장_제출_확정하면_본인과_조회_권한자에게_보인다() throws Exception {
        as(org.seo, get("/api/me/evaluations/todo"))
                .andExpect(jsonPath("$.data[*].evaluationId").value(contains(Math.toIntExact(choEval))))
                .andExpect(jsonPath("$.data[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data[0].templateName").value("일반 평가"));
        as(org.seo, get("/api/evaluations/" + choEval))
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.criteria[0].questions[0].score").value(nullValue()))
                .andExpect(jsonPath("$.data.totalScore").value(nullValue()));

        // 일부만 — 작성중, 답이 빠진 항목은 점수 없음
        answer(org.seo, choEval, "{\"answers\": [{\"questionId\": %d, \"score\": 4}]}".formatted(q1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.criteria[0].questions[0].score").value(4))
                .andExpect(jsonPath("$.data.criteria[0].itemScore").value(nullValue()));
        as(org.seo, post("/api/evaluations/" + choEval + "/submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EVAL_INCOMPLETE"));

        // 성과 (4+5)/2 = 4.5 → 4.5/5×60 = 54.0, 협업 3 → 24.0, 총점 78.0
        answer(org.seo, choEval, full(4, 5, 3, "배포 자동화를 주도했습니다"))
                .andExpect(jsonPath("$.data.criteria[0].itemScore").value(4.5))
                .andExpect(jsonPath("$.data.criteria[0].convertedScore").value(54.0))
                .andExpect(jsonPath("$.data.criteria[1].convertedScore").value(24.0))
                .andExpect(jsonPath("$.data.totalScore").value(78.0));
        as(org.seo, post("/api/evaluations/" + choEval + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.editable").value(false));
        answer(org.seo, choEval, full(1, 1, 1, "고침")).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

        // 확정 전에는 본인에게 안 보인다
        as(org.cho, get("/api/me/evaluations")).andExpect(jsonPath("$.data", empty()));
        as(hr, post("/api/evaluations/" + choEval + "/confirm"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").exists());
        as(hr, post("/api/evaluations/" + choEval + "/confirm")).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

        as(org.cho, get("/api/me/evaluations"))
                .andExpect(jsonPath("$.data[0].evaluationId").value(choEval))
                .andExpect(jsonPath("$.data[0].cycleName").value("상반기"))
                .andExpect(jsonPath("$.data[0].criteria[*].convertedScore").value(contains(54.0, 24.0)))
                .andExpect(jsonPath("$.data[0].criteria[0].questions").doesNotExist())   // 질문별 점수 없음
                .andExpect(jsonPath("$.data[0].totalScore").value(78.0))
                .andExpect(jsonPath("$.data[0].overallComment").value("배포 자동화를 주도했습니다"));

        // 조회 권한자 — 개발본부장은 팀 범위에 조현우가 있고, 백엔드팀장은 없다
        as(org.kang, get("/api/evaluations/results?cycleId=" + cycle))
                .andExpect(jsonPath("$.data.content[*].targetId").value(contains(Math.toIntExact(org.cho.id()))))
                .andExpect(jsonPath("$.data.content[0].evaluatorName").exists())
                .andExpect(jsonPath("$.data.content[0].totalScore").value(78.0));
        as(org.yoon, get("/api/evaluations/results")).andExpect(jsonPath("$.data.totalElements").value(0));
        as(hr, get("/api/evaluations/results?orgUnitId=" + org.backendTeam)).andExpect(jsonPath("$.data.totalElements").value(0));

        // 진행 현황 — 확정 1건, 나머지는 작성 전. 총점은 제출 · 확정만
        String progress = "$.data.items[?(@.evaluationId == %d)].totalScore";
        as(hr, get("/api/eval-cycles/" + cycle + "/progress"))
                .andExpect(jsonPath("$.data.counts.CONFIRMED").value(1))
                .andExpect(jsonPath("$.data.counts.REOPENED").value(0))
                .andExpect(jsonPath("$.data.counts.NOT_STARTED").value(5))
                .andExpect(jsonPath(progress.formatted(choEval)).value(contains(78.0)))
                .andExpect(jsonPath(progress.formatted(yoonEval)).value(contains(nullValue())));
        as(org.seo, get("/api/eval-cycles/" + cycle + "/progress")).andExpect(status().isForbidden());
    }

    @Test
    void 평가자만_입력하고_기간이_끝나면_막히고_재오픈된_건만_다시_고친다() throws Exception {
        // 평가자가 아니면 볼 수도 고칠 수도 없다 — 관리자는 볼 수만
        as(org.cho, get("/api/evaluations/" + choEval)).andExpect(status().isForbidden());
        as(hr, get("/api/evaluations/" + choEval))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(false));
        answer(hr, choEval, full(3, 3, 3, "대신 입력")).andExpect(status().isForbidden());
        // 템플릿에 없는 질문 · 범위 밖 점수
        answer(org.seo, choEval, "{\"answers\": [{\"questionId\": 999999999, \"score\": 3}]}")
                .andExpect(jsonPath("$.error.fields.answers").exists());
        answer(org.seo, choEval, "{\"answers\": [{\"questionId\": %d, \"score\": 6}]}".formatted(q1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['answers[0].score']").exists());

        // 윤의 평가는 마감 전에 확정
        answer(org.kang, yoonEval, full(5, 5, 5, "훌륭함")).andExpect(status().isOk());
        as(org.kang, post("/api/evaluations/" + yoonEval + "/submit")).andExpect(status().isOk());
        as(hr, post("/api/evaluations/" + yoonEval + "/confirm")).andExpect(status().isOk());

        as(hr, post("/api/eval-cycles/" + cycle + "/close")).andExpect(status().isOk());
        answer(org.seo, choEval, full(3, 3, 3, "늦음"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EVAL_PERIOD_CLOSED"));
        as(org.seo, get("/api/me/evaluations/todo")).andExpect(jsonPath("$.data", empty()));

        // 재오픈 — 사유 필수, 확정된 건만
        send(post("/api/evaluations/" + yoonEval + "/reopen"), "{\"reason\": \"\"}")
                .andExpect(jsonPath("$.error.fields.reason").exists());
        send(post("/api/evaluations/" + choEval + "/reopen"), "{\"reason\": \"점수 오류\"}")
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        send(post("/api/evaluations/" + yoonEval + "/reopen"), "{\"reason\": \"점수 오류\"}")
                .andExpect(jsonPath("$.data.status").value("REOPENED"))
                .andExpect(jsonPath("$.data.reopenReason").value("점수 오류"));
        // 재오픈되면 본인 화면에서 빠지고, 평가자의 할 일에 다시 나온다(기간이 끝났어도)
        as(org.yoon, get("/api/me/evaluations")).andExpect(jsonPath("$.data", empty()));
        as(org.kang, get("/api/me/evaluations/todo"))
                .andExpect(jsonPath("$.data[*].evaluationId").value(hasItem(Math.toIntExact(yoonEval))));
        answer(org.kang, yoonEval, full(4, 4, 4, "다시 평가"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REOPENED"))
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.totalScore").value(80.0));
        as(org.kang, post("/api/evaluations/" + yoonEval + "/submit")).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        as(hr, post("/api/evaluations/" + yoonEval + "/confirm")).andExpect(status().isOk());
        as(org.yoon, get("/api/me/evaluations")).andExpect(jsonPath("$.data[0].totalScore").value(80.0));
        as(org.kang, get("/api/me/evaluations/todo"))
                .andExpect(jsonPath("$.data[*].evaluationId").value(not(hasItem(Math.toIntExact(yoonEval)))));

        // 없는 · 다른 회사 ID
        as(hr, get("/api/evaluations/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        as(hr, get("/api/eval-cycles/" + Long.MAX_VALUE + "/progress")).andExpect(status().isNotFound());
    }
}
