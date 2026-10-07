package com.nexuslabs.hr.domain.leave;

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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-LEAVE-01 휴가 종류 관리 (API 설계서 8장). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveTypeTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("휴가종류테스트");
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long typeId(String name) {
        return jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = ?",
                Long.class, company.id(), name);
    }

    private static final String REFRESH = """
            {"name": "리프레시휴가", "annualDays": 5, "deductsBalance": true}
            """;

    @Test
    void 기본_종류_3개는_로그인만_하면_본다() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        mvc.perform(get("/api/leave-types").header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("연차"))
                .andExpect(jsonPath("$.data[0].annualDays").value(15))
                .andExpect(jsonPath("$.data[0].seniorityMaxDays").value(25))
                .andExpect(jsonPath("$.data[1].name").value("병가"))
                .andExpect(jsonPath("$.data[1].deductsBalance").value(false));
    }

    @Test
    void 등록하면_선택값은_기본값으로_맨_뒤에() throws Exception {
        asAdmin(json(post("/api/leave-types"), REFRESH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("리프레시휴가"))
                .andExpect(jsonPath("$.data.paid").value(true))
                .andExpect(jsonPath("$.data.prorateFirstYear").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(4))
                .andExpect(jsonPath("$.data.active").value(true));

        asAdmin(json(post("/api/leave-types"), REFRESH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
    }

    @Test
    void 근속_가산은_4개_모두_또는_모두_비움() throws Exception {
        asAdmin(json(post("/api/leave-types"), """
                {"name": "특별휴가", "annualDays": 3, "deductsBalance": true, "seniorityStartYears": 3}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        asAdmin(json(post("/api/leave-types"), """
                {"name": "특별휴가", "annualDays": 10, "deductsBalance": true, "seniorityStartYears": 1,
                 "seniorityIntervalYears": 1, "seniorityAddDays": 1, "seniorityMaxDays": 5}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수정은_전체_값을_보내고_정렬_활성은_빼면_그대로() throws Exception {
        long id = typeId("연차");
        asAdmin(json(patch("/api/leave-types/" + id), """
                {"name": "연차", "annualDays": 20, "deductsBalance": true, "prorateFirstYear": true}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annualDays").value(20))
                .andExpect(jsonPath("$.data.seniorityStartYears").doesNotExist())
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void 쓰인_적_있으면_비활성화_아니면_삭제() throws Exception {
        long annual = typeId("연차");
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2026, 'REGULAR', 15)""", company.id(), company.adminId(), annual);
        asAdmin(delete("/api/leave-types/" + annual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        asAdmin(get("/api/leave-types").param("activeOnly", "true"))
                .andExpect(jsonPath("$.data.length()").value(2));

        asAdmin(delete("/api/leave-types/" + typeId("경조사")))
                .andExpect(jsonPath("$.data.result").value("DELETED"));
        asAdmin(get("/api/leave-types"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 관리는_LEAVE_MANAGE만_다른_회사_종류는_404() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        mvc.perform(json(post("/api/leave-types"), REFRESH)
                        .header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isForbidden());

        TestFixture.Company other = fixture.company("다른회사");
        long otherType = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, other.id());
        asAdmin(delete("/api/leave-types/" + otherType)).andExpect(status().isNotFound());
    }
}
