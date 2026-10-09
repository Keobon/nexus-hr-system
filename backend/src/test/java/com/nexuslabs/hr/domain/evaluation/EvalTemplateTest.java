package com.nexuslabs.hr.domain.evaluation;

import com.jayway.jsonpath.JsonPath;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EVAL-02 평가 템플릿 (API 설계서 12.1). 템플릿 + 항목 + 질문을 통째로 저장, 가중치 합 100, 항목마다 질문 1개 이상.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvalTemplateTest {

    static final String GENERAL = """
            {"name": "일반 평가", "criteria": [
              {"category": "성과", "name": "업무 성과", "weight": 50, "questions": [
                {"content": "목표한 업무를 기한 안에 완료했는가"}, {"content": "결과물의 품질이 기대 이상인가"}]},
              {"category": "역량", "name": "직무 역량", "weight": 30, "questions": [{"content": "필요한 기술을 갖췄는가"}]},
              {"category": "태도", "name": "협업 태도", "weight": 20, "questions": [{"content": "동료와 협력하는가"}]}]}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    TestFixture.Employee hr;

    @BeforeEach
    void setUp() {
        company = fixture.company("평가템플릿");
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), company.id())));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(hr, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long create(String body) throws Exception {
        String response = send(post("/api/eval-templates"), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    @Test
    void 템플릿은_항목과_질문을_한_번에_저장하고_정렬_순서를_비우면_보낸_순서() throws Exception {
        long id = create(GENERAL);
        as(hr, get("/api/eval-templates/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("일반 평가"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.inUse").value(false))
                .andExpect(jsonPath("$.data.copiedFromId").isEmpty())
                .andExpect(jsonPath("$.data.criteria[*].name").value(contains("업무 성과", "직무 역량", "협업 태도")))
                .andExpect(jsonPath("$.data.criteria[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.criteria[0].weight").value(50))
                .andExpect(jsonPath("$.data.criteria[0].questions[1].content").value("결과물의 품질이 기대 이상인가"))
                .andExpect(jsonPath("$.data.criteria[0].questions[1].sortOrder").value(2))
                .andExpect(jsonPath("$.data.criteria[0].questions[1].id").isNumber());
        as(hr, get("/api/eval-templates"))
                .andExpect(jsonPath("$.data[0].name").value("일반 평가"))
                .andExpect(jsonPath("$.data[0].criteriaCount").value(3))
                .andExpect(jsonPath("$.data[0].questionCount").value(4));

        // PUT 은 통째로 교체 — 항목 · 질문이 새로 만들어진다
        send(put("/api/eval-templates/" + id), """
                {"name": "일반 평가 v2", "criteria": [
                  {"category": "성과", "name": "성과", "weight": 100, "questions": [{"content": "잘했는가"}]}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("일반 평가 v2"))
                .andExpect(jsonPath("$.data.criteria.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eval_question q JOIN eval_criteria c ON c.id = q.eval_criteria_id "
                + "WHERE c.eval_template_id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void 저장_거부_규칙() throws Exception {
        send(post("/api/eval-templates"), GENERAL.replace("\"weight\": 20", "\"weight\": 10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EVAL_WEIGHT_SUM_INVALID"));
        send(post("/api/eval-templates"), """
                {"name": "질문 없음", "criteria": [{"category": "성과", "name": "성과", "weight": 100, "questions": []}]}""")
                .andExpect(jsonPath("$.error.code").value("EVAL_NO_QUESTION"));
        send(post("/api/eval-templates"), """
                {"name": "", "criteria": [{"category": "성과", "name": "성과", "weight": 0, "questions": [{"content": "?"}]}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields['criteria[0].weight']").exists());
        create(GENERAL);
        send(post("/api/eval-templates"), GENERAL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        // 직원 역할은 EVAL_MANAGE 가 없다
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        as(staff, get("/api/eval-templates")).andExpect(status().isForbidden());
    }

    @Test
    void 시작된_평가에_쓰인_템플릿은_수정_불가_복사해서_고치고_삭제는_비활성화() throws Exception {
        long used = create(GENERAL);
        long unused = create(GENERAL.replace("일반 평가", "안 쓴 평가"));
        // 시작된 평가 1건(평가 기간 진행 중)
        long cycle = jdbc.queryForObject("""
                INSERT INTO eval_cycle (company_id, name, start_date, end_date, status)
                VALUES (?, '상반기', '2030-01-01', '2030-01-31', 'IN_PROGRESS') RETURNING id""", Long.class, company.id());
        TestFixture.Employee target = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        jdbc.update("""
                INSERT INTO evaluation (company_id, eval_cycle_id, target_employee_id, evaluator_id, eval_template_id)
                VALUES (?, ?, ?, ?, ?)""", company.id(), cycle, target.id(), hr.id(), used);

        as(hr, get("/api/eval-templates/" + used)).andExpect(jsonPath("$.data.inUse").value(true));
        send(put("/api/eval-templates/" + used), GENERAL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EVAL_TEMPLATE_IN_USE"));

        send(post("/api/eval-templates/" + used + "/copy"), "{\"name\": \"일반 평가 (복사)\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.copiedFromId").value(used))
                .andExpect(jsonPath("$.data.inUse").value(false))
                .andExpect(jsonPath("$.data.criteria[0].questions.length()").value(2))
                .andExpect(jsonPath("$.data.criteria[*].weight").value(contains(50, 30, 20)));
        send(post("/api/eval-templates/" + used + "/copy"), "{\"name\": \"안 쓴 평가\"}")
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));

        as(hr, delete("/api/eval-templates/" + used)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        as(hr, delete("/api/eval-templates/" + unused)).andExpect(jsonPath("$.data.result").value("DELETED"));
        as(hr, get("/api/eval-templates?activeOnly=true"))
                .andExpect(jsonPath("$.data[*].id").value(not(hasItem(Math.toIntExact(used)))));
        assertThat(jdbc.queryForList("SELECT name FROM eval_template WHERE company_id = ? ORDER BY name", String.class,
                company.id())).isEqualTo(List.of("일반 평가", "일반 평가 (복사)"));
    }
}
