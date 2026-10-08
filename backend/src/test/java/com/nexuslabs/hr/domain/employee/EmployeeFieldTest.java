package com.nexuslabs.hr.domain.employee;

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

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EMP-07 직원 추가 항목 정의 · F-EMP-03 값 교체 (API 설계서 6장, BR-EMP-005).
 * 인사 담당 역할은 ORG_MANAGE · EMPLOYEE_MANAGE 가 있다. JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class EmployeeFieldTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    TestFixture.Employee hr;
    TestFixture.Employee kim;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("추가항목테스트");
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), company.id())));
    }

    private ResultActions json(TestFixture.Employee e, MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(e, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long field(String body) throws Exception {
        String res = json(hr, post("/api/employee-fields"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private ResultActions values(TestFixture.Employee e, String values) throws Exception {
        return json(hr, put("/api/employees/" + e.id() + "/field-values"), "{\"values\": [" + values + "]}");
    }

    @Test
    void 항목_정의를_등록하고_보낸_필드만_고친다() throws Exception {
        json(hr, post("/api/employee-fields"), """
                {"name": " 학력 ", "fieldType": "TEXT", "options": ["무시"], "isMultiple": true, "isSelfEditable": true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("학력"))
                .andExpect(jsonPath("$.data.options").value(nullValue()))         // SELECT 가 아니면 선택지 무시
                .andExpect(jsonPath("$.data.isRequired").value(false))
                .andExpect(jsonPath("$.data.isMultiple").value(true))
                .andExpect(jsonPath("$.data.isSelfEditable").value(true))
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andExpect(jsonPath("$.data.isActive").value(true));
        long blood = field("{\"name\": \"혈액형\", \"fieldType\": \"SELECT\", \"options\": [\"A\", \"B\"], \"isRequired\": true}");

        json(hr, post("/api/employee-fields"), "{\"name\": \"학력\", \"fieldType\": \"TEXT\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        json(hr, post("/api/employee-fields"), "{\"name\": \"자격\", \"fieldType\": \"SELECT\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.options").exists());
        json(hr, post("/api/employee-fields"), "{\"name\": \"자격\", \"fieldType\": \"SELECT\", \"options\": [\"A\", \"A \"]}")
                .andExpect(jsonPath("$.error.fields.options").value("선택지가 겹칩니다"));
        json(kim, post("/api/employee-fields"), "{\"name\": \"자격\", \"fieldType\": \"TEXT\"}")
                .andExpect(status().isForbidden());

        json(hr, patch("/api/employee-fields/" + blood), "{\"options\": [\"A\", \"B\", \"O\", \"AB\"], \"sortOrder\": 9}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("혈액형"))
                .andExpect(jsonPath("$.data.options").value(contains("A", "B", "O", "AB")))
                .andExpect(jsonPath("$.data.isRequired").value(true))
                .andExpect(jsonPath("$.data.sortOrder").value(9));
        json(hr, patch("/api/employee-fields/" + blood), "{\"name\": \"학력\"}")
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        json(hr, patch("/api/employee-fields/" + blood), "{\"fieldType\": \"BOOLEAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.error.details.allowed").isArray());
        json(hr, patch("/api/employee-fields/" + blood), "{\"isRequired\": null, \"color\": 1}")
                .andExpect(jsonPath("$.error.fields.isRequired").exists())
                .andExpect(jsonPath("$.error.fields.color").exists());

        // 로그인만 하면 정의를 본다 — 정렬 순서대로
        as(kim, get("/api/employee-fields")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(contains("학력", "혈액형")));
    }

    @Test
    void 값이_있는_항목은_타입을_못_바꾸고_삭제하면_비활성화된다() throws Exception {
        long edu = field("{\"name\": \"학력\", \"fieldType\": \"TEXT\", \"isMultiple\": true}");
        long memo = field("{\"name\": \"비고\", \"fieldType\": \"LONG_TEXT\"}");
        values(kim, "{\"fieldDefId\": %d, \"value\": \"OO고 졸업\"}, {\"fieldDefId\": %d, \"value\": \"OO대 졸업\"}"
                .formatted(edu, edu)).andExpect(status().isOk());

        json(hr, patch("/api/employee-fields/" + edu), "{\"fieldType\": \"NUMBER\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ITEM_IN_USE"));
        json(hr, patch("/api/employee-fields/" + edu), "{\"isMultiple\": false}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ITEM_IN_USE"));
        json(hr, patch("/api/employee-fields/" + memo), "{\"fieldType\": \"DATE\"}")    // 값이 없으면 된다
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fieldType").value("DATE"));

        as(hr, delete("/api/employee-fields/" + edu)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        as(hr, delete("/api/employee-fields/" + memo)).andExpect(jsonPath("$.data.result").value("DELETED"));
        as(kim, get("/api/employee-fields?activeOnly=true")).andExpect(jsonPath("$.data", hasSize(0)));
        as(hr, get("/api/employee-fields")).andExpect(jsonPath("$.data[0].isActive").value(false));
        // 값은 남고 화면에서는 숨는다
        as(kim, get("/api/me/profile")).andExpect(jsonPath("$.data.fieldValues", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee_field_value WHERE employee_id = ?",
                Long.class, kim.id())).isEqualTo(2);
    }

    @Test
    void 관리자는_활성_항목_값을_전체_교체한다() throws Exception {
        long edu = field("{\"name\": \"학력\", \"fieldType\": \"TEXT\", \"isMultiple\": true, \"isSelfEditable\": true}");
        long blood = field("{\"name\": \"혈액형\", \"fieldType\": \"SELECT\", \"options\": [\"A\", \"B\"]}");
        long hidden = field("{\"name\": \"옛 항목\", \"fieldType\": \"TEXT\"}");
        values(kim, "{\"fieldDefId\": %d, \"value\": \"보존\"}".formatted(hidden)).andExpect(status().isOk());
        as(hr, delete("/api/employee-fields/" + hidden));

        values(kim, "{\"fieldDefId\": %d, \"value\": \"OO고\"}, {\"fieldDefId\": %d, \"value\": \"OO대\"}, {\"fieldDefId\": %d, \"value\": \"B\"}"
                .formatted(edu, edu, blood))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].value").value(contains("OO고", "OO대", "B")))
                .andExpect(jsonPath("$.data[1].seq").value(2));
        values(kim, "{\"fieldDefId\": %d, \"value\": \"A\"}".formatted(blood))
                .andExpect(jsonPath("$.data[*].value").value(contains("A")));            // 학력은 지워짐

        // 필수로 바꾸면 다음 수정부터 필수 — 기존 빈 값은 그대로
        json(hr, patch("/api/employee-fields/" + edu), "{\"isRequired\": true}").andExpect(status().isOk());
        as(kim, get("/api/me/profile")).andExpect(jsonPath("$.data.fieldValues", hasSize(1)));
        values(kim, "{\"fieldDefId\": %d, \"value\": \"A\"}".formatted(blood))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['values." + edu + "']").exists());
        values(kim, "{\"fieldDefId\": %d, \"value\": \"OO대\"}, {\"fieldDefId\": %d, \"value\": \"C\"}".formatted(edu, blood))
                .andExpect(jsonPath("$.error.fields['values." + blood + "']").exists());   // 선택지에 없음
        values(kim, "{\"fieldDefId\": %d, \"value\": \"x\"}, {\"fieldDefId\": %d, \"value\": \"x\"}".formatted(edu, hidden))
                .andExpect(jsonPath("$.error.fields['values." + hidden + "']").exists());  // 비활성 항목
        // 비활성 항목 값은 교체 대상이 아니라 남는다
        assertThat(jdbc.queryForObject("SELECT value FROM employee_field_value WHERE employee_id = ? AND field_def_id = ?",
                String.class, kim.id(), hidden)).isEqualTo("보존");

        as(kim, put("/api/employees/" + hr.id() + "/field-values").contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\": []}")).andExpect(status().isForbidden());
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", kim.id());
        values(kim, "{\"fieldDefId\": %d, \"value\": \"OO대\"}".formatted(edu))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
        TestFixture.Company other = fixture.company("추가항목타사");
        TestFixture.Employee stranger = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false);
        values(stranger, "").andExpect(status().isNotFound());
    }

    @Test
    void 본인은_본인_수정_가능_항목만_교체한다() throws Exception {
        long edu = field("{\"name\": \"학력\", \"fieldType\": \"TEXT\", \"isMultiple\": true, \"isSelfEditable\": true}");
        long cert = field("{\"name\": \"자격증\", \"fieldType\": \"DATE\", \"isSelfEditable\": true}");
        long blood = field("{\"name\": \"혈액형\", \"fieldType\": \"SELECT\", \"options\": [\"A\", \"B\"], \"isRequired\": true}");
        values(kim, "{\"fieldDefId\": %d, \"value\": \"B\"}, {\"fieldDefId\": %d, \"value\": \"2029-01-01\"}"
                .formatted(blood, cert)).andExpect(status().isOk());

        json(kim, put("/api/me/field-values"), "{\"values\": [{\"fieldDefId\": %d, \"value\": \"OO대\"}]}".formatted(edu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].value").value(contains("OO대", "B")));    // 자격증은 지워지고 혈액형은 그대로
        json(kim, put("/api/me/field-values"), "{\"values\": [{\"fieldDefId\": %d, \"value\": \"A\"}]}".formatted(blood))
                .andExpect(status().isForbidden());
        json(kim, put("/api/me/field-values"), "{\"values\": [{\"fieldDefId\": %d, \"value\": \"어제\"}]}".formatted(cert))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['values." + cert + "']").exists());
        json(kim, put("/api/me/field-values"), "{\"values\": [{\"fieldDefId\": %d, \"value\": \" \"}]}".formatted(edu))
                .andExpect(status().isBadRequest());
    }
}
