package com.nexuslabs.hr.domain.account;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-AUTH-05 역할 관리 (API 설계서 4장). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("역할테스트");
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private long roleId(String name) {
        return jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?", Long.class, company.id(), name);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static final String HR_TEAM = """
            {"name": "인사팀", "description": "인사·급여 담당",
             "permissions": [{"code": "EMPLOYEE_READ", "scope": "TEAM"}, {"code": "PAYROLL_READ", "scope": "ALL"},
                             {"code": "ROLE_MANAGE", "scope": "ALL"}]}
            """;

    @Test
    void 권한_코드_목록은_고를_수_있는_범위와_함께() throws Exception {
        asAdmin(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(18))
                .andExpect(jsonPath("$.data[0].code").value("COMPANY_MANAGE"))
                .andExpect(jsonPath("$.data[0].allowedScopes").value(contains("ALL")))
                .andExpect(jsonPath("$.data[0].description").value("회사 정보와 변경 이력 · 근무시간 · 휴일 · 초기 설정 · 회사 서류"))
                .andExpect(jsonPath("$.data[0].relatedFeatures").value("F-COMP-02–05 · 07"))
                .andExpect(jsonPath("$.data[5].code").value("EMPLOYEE_READ"))
                .andExpect(jsonPath("$.data[5].allowedScopes").value(contains("TEAM", "ALL")));
    }

    @Test
    void 역할_목록은_ROLE_MANAGE_또는_EMPLOYEE_MANAGE() throws Exception {
        asAdmin(get("/api/roles"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].name").value("최고 관리자"))
                .andExpect(jsonPath("$.data[0].isSystem").value(true))
                .andExpect(jsonPath("$.data[0].accountCount").value(1));

        TestFixture.Employee hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
        mvc.perform(get("/api/roles").header("Authorization", fixture.token(hr.id(), company.id())))
                .andExpect(status().isOk());
        // 인사 담당은 ROLE_MANAGE 가 없어 역할 상세·권한 목록은 못 본다
        mvc.perform(get("/api/permissions").header("Authorization", fixture.token(hr.id(), company.id())))
                .andExpect(status().isForbidden());

        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        mvc.perform(get("/api/roles").header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 역할을_만들면_감사_로그가_남고_배정된_사람에게_바로_적용된다() throws Exception {
        String body = asAdmin(json(post("/api/roles"), HR_TEAM))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("인사팀"))
                .andExpect(jsonPath("$.data.isSystem").value(false))
                .andExpect(jsonPath("$.data.permissions.length()").value(3))
                .andExpect(jsonPath("$.data.permissions[0].code").value("ROLE_MANAGE"))
                .andReturn().getResponse().getContentAsString();
        long newRoleId = Long.parseLong(body.replaceAll(".*\"data\":\\{\"id\":(\\d+).*", "$1"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'ROLE' AND target_id = ? AND action = 'CREATE'",
                Long.class, company.id(), newRoleId)).isEqualTo(1);

        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        mvc.perform(get("/api/permissions").header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE account SET role_id = ? WHERE employee_id = ?", newRoleId, staff.id());
        mvc.perform(get("/api/permissions").header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isOk());
    }

    @Test
    void 권한_검증() throws Exception {
        // 급여 조회는 전사 전용
        asAdmin(json(post("/api/roles"), """
                {"name": "x", "permissions": [{"code": "PAYROLL_READ", "scope": "TEAM"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.code").value("PAYROLL_READ"));
        asAdmin(json(post("/api/roles"), """
                {"name": "x", "permissions": [{"code": "LEAVE_READ", "scope": "TEAM"}, {"code": "LEAVE_READ", "scope": "ALL"}]}"""))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        asAdmin(json(post("/api/roles"), """
                {"name": "x", "permissions": [{"code": "NOPE", "scope": "ALL"}]}"""))
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
        asAdmin(json(post("/api/roles"), """
                {"name": "직원", "permissions": []}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
    }

    @Test
    void 수정하면_권한_목록을_통째로_바꾼다() throws Exception {
        long id = roleId("경영진");
        asAdmin(json(put("/api/roles/" + id), """
                {"name": "임원", "description": null, "permissions": [{"code": "DASHBOARD_COMPANY", "scope": "ALL"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("임원"))
                .andExpect(jsonPath("$.data.permissions.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permission WHERE role_id = ?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_type = 'ROLE' AND target_id = ? AND action = 'UPDATE'",
                Long.class, id)).isEqualTo(1);
        // 자기 이름 그대로 저장은 중복이 아니다
        asAdmin(json(put("/api/roles/" + id), """
                {"name": "임원", "permissions": []}""")).andExpect(status().isOk());
    }

    @Test
    void 최고_관리자_역할은_바꾸거나_지울_수_없다() throws Exception {
        long id = roleId("최고 관리자");
        asAdmin(json(put("/api/roles/" + id), """
                {"name": "관리자", "permissions": []}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SYSTEM_ROLE_READONLY"));
        asAdmin(delete("/api/roles/" + id))
                .andExpect(jsonPath("$.error.code").value("SYSTEM_ROLE_READONLY"));
    }

    @Test
    void 계정이_배정된_역할은_지울_수_없다() throws Exception {
        fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        asAdmin(delete("/api/roles/" + roleId("직원")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_IN_USE"))
                .andExpect(jsonPath("$.error.details.accountCount").value(1));

        long execId = roleId("경영진");
        asAdmin(delete("/api/roles/" + execId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DELETED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role WHERE id = ?", Long.class, execId)).isZero();
    }

    @Test
    void 다른_회사_역할은_404() throws Exception {
        TestFixture.Company other = fixture.company("다른회사");
        long otherRole = jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = '직원'", Long.class, other.id());
        asAdmin(get("/api/roles/" + otherRole)).andExpect(status().isNotFound());
        asAdmin(json(put("/api/roles/" + otherRole), """
                {"name": "x", "permissions": []}""")).andExpect(status().isNotFound());
        asAdmin(delete("/api/roles/" + otherRole)).andExpect(status().isNotFound());
    }
}
