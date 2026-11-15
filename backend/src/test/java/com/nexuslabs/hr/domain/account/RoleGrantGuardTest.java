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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인사 담당 기본 역할에 ROLE_MANAGE(2026-11-15) — 대신 "내 권한 범위를 넘는 역할은 만들 수도, 줄 수도, 그 역할의 계정을
 * 다룰 수도 없다". 최고 관리자 역할은 18개 권한을 모두 가지므로 최고 관리자만 그 역할을 주고 그 계정을 다룬다.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleGrantGuardTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    TestFixture.Employee admin;
    TestFixture.Employee hr;
    TestFixture.Employee kim;

    @BeforeEach
    void setUp() {
        company = fixture.company("권한상승");
        admin = new TestFixture.Employee(company.adminId(), company.id(), company.adminEmail());
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), company.id())));
    }

    private ResultActions json(TestFixture.Employee e, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return as(e, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long role(String name) {
        return jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?", Long.class, company.id(), name);
    }

    private long roleOf(TestFixture.Employee e) {
        return jdbc.queryForObject("SELECT role_id FROM account WHERE employee_id = ?", Long.class, e.id());
    }

    private ResultActions changeRole(TestFixture.Employee actor, TestFixture.Employee target, long roleId) throws Exception {
        return json(actor, patch("/api/accounts/" + target.id()), "{\"roleId\": " + roleId + "}");
    }

    @Test
    void 인사_담당은_기본으로_역할_관리를_한다() throws Exception {
        as(hr, get("/api/accounts")).andExpect(status().isOk());
        as(hr, get("/api/permissions")).andExpect(status().isOk());
        // 내 권한 안의 역할은 줄 수 있다 — 경영진(전사 조회 + 대시보드)
        changeRole(hr, kim, role("경영진")).andExpect(status().isOk());
        assertThat(roleOf(kim)).isEqualTo(role("경영진"));
        as(hr, post("/api/accounts/" + kim.id() + "/reset-password")).andExpect(status().isOk());
    }

    @Test
    void 최고_관리자_역할은_최고_관리자만_주고_그_계정도_최고_관리자만_다룬다() throws Exception {
        changeRole(hr, kim, role("최고 관리자"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        changeRole(hr, hr, role("최고 관리자")).andExpect(status().isForbidden());
        assertThat(roleOf(hr)).isEqualTo(role("인사 담당"));
        // 최고 관리자 계정 — 역할 바꾸기 · 비활성 · 임시 비밀번호 모두 막는다
        changeRole(hr, admin, role("직원")).andExpect(status().isForbidden());
        json(hr, patch("/api/accounts/" + admin.id()), "{\"active\": false}").andExpect(status().isForbidden());
        as(hr, post("/api/accounts/" + admin.id() + "/reset-password")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM account WHERE employee_id = ?",
                Boolean.class, admin.id())).isFalse();
        // 최고 관리자는 된다
        changeRole(admin, kim, role("최고 관리자")).andExpect(status().isOk());
    }

    @Test
    void 내게_없는_권한이_든_역할은_만들거나_바꾸거나_줄_수_없다() throws Exception {
        // 인사 담당이 가진 권한만이면 된다(전사 → 팀 범위로 좁혀도 된다)
        json(hr, post("/api/roles"), """
                {"name": "급여 조회", "description": "", "permissions": [{"code": "PAYROLL_READ", "scope": "ALL"},
                 {"code": "EMPLOYEE_READ", "scope": "TEAM"}]}""").andExpect(status().isCreated());
        // 감사 로그 조회(AUDIT_READ)는 인사 담당에게 없다
        json(hr, post("/api/roles"), """
                {"name": "감사", "description": "", "permissions": [{"code": "AUDIT_READ", "scope": "ALL"}]}""")
                .andExpect(status().isForbidden());
        json(hr, put("/api/roles/" + role("직원")), """
                {"name": "직원", "description": "", "permissions": [{"code": "COMPANY_MANAGE", "scope": "ALL"}]}""")
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permission WHERE role_id = ? AND permission_code = 'COMPANY_MANAGE'",
                Integer.class, role("직원"))).isZero();

        // 최고 관리자가 만든 넓은 역할 — 인사 담당은 그 역할을 주지도, 그 역할의 계정을 다루지도 못한다
        json(admin, post("/api/roles"), """
                {"name": "감사", "description": "", "permissions": [{"code": "AUDIT_READ", "scope": "ALL"}]}""")
                .andExpect(status().isCreated());
        changeRole(hr, kim, role("감사")).andExpect(status().isForbidden());
        json(hr, put("/api/roles/" + role("감사")), """
                {"name": "감사", "description": "", "permissions": [{"code": "PAYROLL_READ", "scope": "ALL"}]}""")
                .andExpect(status().isForbidden());
        changeRole(admin, kim, role("감사")).andExpect(status().isOk());
        as(hr, post("/api/accounts/" + kim.id() + "/reset-password")).andExpect(status().isForbidden());
        changeRole(hr, kim, role("직원")).andExpect(status().isForbidden());
    }

    @Test
    void 목록은_내가_다룰_수_있는_계정과_줄_수_있는_역할을_알려_준다() throws Exception {
        TestFixture.Employee gone = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        jdbc.update("UPDATE employee SET status = 'RESIGNED', resign_date = '2026-01-01', resign_reason = 'ETC' WHERE id = ?",
                gone.id());
        String row = "$.data.content[?(@.employeeId == %d)].%s";
        as(hr, get("/api/accounts").param("size", "50")).andExpect(status().isOk())
                .andExpect(jsonPath(row.formatted(admin.id(), "manageable")).value(contains(false)))
                .andExpect(jsonPath(row.formatted(kim.id(), "manageable")).value(contains(true)))
                .andExpect(jsonPath(row.formatted(hr.id(), "manageable")).value(contains(true)))
                .andExpect(jsonPath(row.formatted(kim.id(), "resigned")).value(contains(false)))
                .andExpect(jsonPath(row.formatted(gone.id(), "resigned")).value(contains(true)));
        as(admin, get("/api/accounts").param("size", "50"))
                .andExpect(jsonPath(row.formatted(admin.id(), "manageable")).value(contains(true)));
        // 계정을 바꾼 응답에도 같은 값
        changeRole(hr, kim, role("경영진")).andExpect(jsonPath("$.data.manageable").value(true))
                .andExpect(jsonPath("$.data.resigned").value(false));

        String grantable = "$.data[?(@.name == '%s')].grantable";
        as(hr, get("/api/roles")).andExpect(status().isOk())
                .andExpect(jsonPath(grantable.formatted("최고 관리자")).value(contains(false)))
                .andExpect(jsonPath(grantable.formatted("인사 담당")).value(contains(true)))
                .andExpect(jsonPath(grantable.formatted("경영진")).value(contains(true)))
                .andExpect(jsonPath(grantable.formatted("직원")).value(contains(true)));
        as(admin, get("/api/roles"))
                .andExpect(jsonPath(grantable.formatted("최고 관리자")).value(contains(true)));
        // ROLE_MANAGE 없이 EMPLOYEE_MANAGE 만 있으면(직원 등록) 기본 역할만 줄 수 있다
        long registrar = jdbc.queryForObject(
                "INSERT INTO role (company_id, name, description, is_system) VALUES (?, '등록 담당', '', FALSE) RETURNING id",
                Long.class, company.id());
        jdbc.update("""
                INSERT INTO role_permission (company_id, role_id, permission_code, scope)
                VALUES (?, ?, 'EMPLOYEE_MANAGE', 'ALL'), (?, ?, 'EMPLOYEE_READ', 'ALL')""",
                company.id(), registrar, company.id(), registrar);
        TestFixture.Employee clerk = fixture.employee(company.id(), company.rootOrgUnitId(), "등록 담당", false);
        as(clerk, get("/api/roles")).andExpect(status().isOk())
                .andExpect(jsonPath(grantable.formatted("직원")).value(contains(true)))
                .andExpect(jsonPath(grantable.formatted("등록 담당")).value(contains(false)))
                .andExpect(jsonPath(grantable.formatted("경영진")).value(contains(false)));
    }

    @Test
    void 직원_등록도_내_권한_안의_역할만() throws Exception {
        long grade = jdbc.queryForObject("SELECT id FROM job_grade WHERE company_id = ? AND name = '사원'",
                Long.class, company.id());
        long type = jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '정규직'",
                Long.class, company.id());
        String body = """
                {"name": "신입", "email": "%s.%d@grant.example", "hireDate": "2026-03-03", "orgUnitId": %d,
                 "jobGradeId": %d, "employmentTypeId": %d, "roleId": %d}""";
        json(hr, post("/api/employees"), body.formatted("exec", company.id(), company.rootOrgUnitId(), grade, type,
                role("경영진"))).andExpect(status().isCreated());
        json(hr, post("/api/employees"), body.formatted("boss", company.id(), company.rootOrgUnitId(), grade, type,
                role("최고 관리자"))).andExpect(status().isForbidden());
    }
}
