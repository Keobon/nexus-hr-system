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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-EMP-09 가족 정보 (API 설계서 6장, BR-EMP-007). 시계는 2030-03-04(월). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class EmployeeFamilyTest {

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
        company = fixture.company("가족정보테스트");
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), company.id())));
    }

    private ResultActions json(TestFixture.Employee e, MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(e, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long add(String body) throws Exception {
        String res = json(hr, post("/api/employees/" + kim.id() + "/family"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private long audits() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'EMPLOYEE_FAMILY'",
                Long.class, company.id());
    }

    @Test
    void 부양가족_수와_자녀_수는_공제_대상으로_센다() throws Exception {
        json(hr, post("/api/employees/" + kim.id() + "/family"), """
                {"name": " 이배우 ", "relation": "SPOUSE", "birthDate": "1990-05-01", "isTaxDependent": true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("이배우"))
                .andExpect(jsonPath("$.data.isTaxDependent").value(true))
                .andExpect(jsonPath("$.data.isDisabled").value(false))
                .andExpect(jsonPath("$.data.isCohabiting").value(true));
        add("{\"name\": \"김첫째\", \"relation\": \"CHILD\", \"isTaxDependent\": true}");
        long second = add("{\"name\": \"김둘째\", \"relation\": \"CHILD\", \"birthDate\": \"2025-01-01\"}");
        long parent = add("{\"name\": \"김부모\", \"relation\": \"PARENT\", \"isCohabiting\": false}");

        as(hr, get("/api/employees/" + kim.id() + "/family")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dependentsCount").value(2))
                .andExpect(jsonPath("$.data.childrenCount").value(1))
                .andExpect(jsonPath("$.data.members[*].name").value(contains("이배우", "김첫째", "김둘째", "김부모")));

        json(hr, patch("/api/employees/" + kim.id() + "/family/" + second), "{\"isTaxDependent\": true, \"birthDate\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김둘째"))
                .andExpect(jsonPath("$.data.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.data.isTaxDependent").value(true));
        json(hr, patch("/api/employees/" + kim.id() + "/family/" + second), "{\"name\": null}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.name").exists());
        json(hr, patch("/api/employees/" + kim.id() + "/family/" + second), "{\"relation\": \"COUSIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.error.details.allowed").isArray());
        as(hr, delete("/api/employees/" + kim.id() + "/family/" + parent))
                .andExpect(jsonPath("$.data.result").value("DELETED"));

        // 본인은 같은 모양으로 조회만
        as(kim, get("/api/me/family")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dependentsCount").value(3))
                .andExpect(jsonPath("$.data.childrenCount").value(2))
                .andExpect(jsonPath("$.data.members.length()").value(3));
        assertThat(audits()).isEqualTo(6);                                   // 추가 4 · 수정 1 · 삭제 1
    }

    @Test
    void 가족_정보는_관리자만_고치고_퇴직자는_조회만_된다() throws Exception {
        long spouse = add("{\"name\": \"이배우\", \"relation\": \"SPOUSE\"}");
        json(kim, post("/api/employees/" + kim.id() + "/family"), "{\"name\": \"x\", \"relation\": \"CHILD\"}")
                .andExpect(status().isForbidden());
        as(kim, get("/api/employees/" + kim.id() + "/family")).andExpect(status().isForbidden());
        json(hr, post("/api/employees/" + kim.id() + "/family"),
                "{\"name\": \"x\", \"relation\": \"CHILD\", \"birthDate\": \"2030-03-05\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.birthDate").exists());
        // 다른 직원 경로로 남의 가족을 고칠 수 없다
        json(hr, patch("/api/employees/" + hr.id() + "/family/" + spouse), "{\"name\": \"y\"}")
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", kim.id());
        json(hr, patch("/api/employees/" + kim.id() + "/family/" + spouse), "{\"name\": \"y\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
        as(hr, delete("/api/employees/" + kim.id() + "/family/" + spouse)).andExpect(status().isConflict());
        as(hr, get("/api/employees/" + kim.id() + "/family")).andExpect(status().isOk());

        TestFixture.Company other = fixture.company("가족정보타사");
        TestFixture.Employee stranger = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false);
        as(hr, get("/api/employees/" + stranger.id() + "/family")).andExpect(status().isNotFound());
        json(hr, post("/api/employees/" + stranger.id() + "/family"), "{\"name\": \"x\", \"relation\": \"CHILD\"}")
                .andExpect(status().isNotFound());
        assertThat(audits()).isEqualTo(1);
    }
}
