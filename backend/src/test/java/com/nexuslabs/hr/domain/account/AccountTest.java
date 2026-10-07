package com.nexuslabs.hr.domain.account;

import com.nexuslabs.hr.domain.account.service.AccountService;
import com.nexuslabs.hr.domain.account.service.PasswordRule;
import com.nexuslabs.hr.domain.employee.service.EmployeeNoGenerator;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-AUTH-04 계정 자동 생성 · F-AUTH-06 계정 관리 (API 설계서 4장). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired AccountService accountService;
    @Autowired EmployeeNoGenerator employeeNoGenerator;

    TestFixture.Company company;
    TestFixture.Employee staff;

    @BeforeEach
    void setUp() {
        company = fixture.company("계정테스트");
        staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private ResultActions patchAccount(long employeeId, String body) throws Exception {
        return asAdmin(patch("/api/accounts/" + employeeId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private long roleId(String name) {
        return jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?", Long.class, company.id(), name);
    }

    @Test
    void 계정_목록은_페이징과_필터() throws Exception {
        for (int i = 0; i < 3; i++) {
            fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        }
        fixture.company("다른회사"); // 다른 회사 계정은 섞이지 않는다

        asAdmin(get("/api/accounts").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].roleName").exists())
                .andExpect(jsonPath("$.data.content[0].orgUnitName").value("계정테스트"));
        asAdmin(get("/api/accounts").param("roleId", String.valueOf(roleId("최고 관리자"))))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].employeeId").value(company.adminId()));
        asAdmin(get("/api/accounts").param("keyword", staff.email().substring(0, staff.email().indexOf('@'))))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        jdbc.update("UPDATE account SET locked_until = now() + interval '10 minutes' WHERE employee_id = ?", staff.id());
        asAdmin(get("/api/accounts").param("locked", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].locked").value(true));
        asAdmin(get("/api/accounts").param("locked", "false"))
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    void 계정_관리는_ROLE_MANAGE만() throws Exception {
        mvc.perform(get("/api/accounts").header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 역할을_바꾸면_감사_로그가_남고_바로_적용된다() throws Exception {
        long hrRole = roleId("인사 담당");
        patchAccount(staff.id(), "{\"roleId\": " + hrRole + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("인사 담당"))
                .andExpect(jsonPath("$.data.active").value(true));
        assertThat(jdbc.queryForObject("""
                SELECT (before_value->>'roleId')::bigint <> (after_value->>'roleId')::bigint
                FROM audit_log WHERE target_type = 'ACCOUNT' AND target_id = ?""", Boolean.class, staff.id())).isTrue();
        mvc.perform(get("/api/roles").header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isOk());
    }

    @Test
    void 비활성화하면_로그인할_수_없다() throws Exception {
        patchAccount(staff.id(), "{\"active\": false}").andExpect(jsonPath("$.data.active").value(false));
        login(staff.email(), TestFixture.PASSWORD).andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_INACTIVE"));
    }

    @Test
    void 마지막_최고_관리자는_비활성화하거나_역할을_바꿀_수_없다() throws Exception {
        patchAccount(company.adminId(), "{\"active\": false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_SUPER_ADMIN"));
        patchAccount(company.adminId(), "{\"roleId\": " + roleId("직원") + "}")
                .andExpect(jsonPath("$.error.code").value("LAST_SUPER_ADMIN"));
        // 같은 역할로 저장은 괜찮다
        patchAccount(company.adminId(), "{\"roleId\": " + roleId("최고 관리자") + "}").andExpect(status().isOk());

        // 퇴직한 최고 관리자는 세지 않는다
        TestFixture.Employee resignedAdmin = fixture.employee(company.id(), company.rootOrgUnitId(), "최고 관리자", false);
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", resignedAdmin.id());
        patchAccount(company.adminId(), "{\"active\": false}").andExpect(jsonPath("$.error.code").value("LAST_SUPER_ADMIN"));

        // 다른 활성 최고 관리자가 있으면 된다
        fixture.employee(company.id(), company.rootOrgUnitId(), "최고 관리자", false);
        patchAccount(company.adminId(), "{\"roleId\": " + roleId("직원") + "}").andExpect(status().isOk());
    }

    @Test
    void 퇴직자의_계정은_다시_활성화할_수_없다() throws Exception {
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", staff.id());
        jdbc.update("UPDATE account SET is_active = FALSE WHERE employee_id = ?", staff.id());
        patchAccount(staff.id(), "{\"active\": true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
    }

    @Test
    void 다른_회사의_계정이나_역할은_404() throws Exception {
        TestFixture.Company other = fixture.company("다른회사");
        long otherRole = jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = '직원'", Long.class, other.id());
        patchAccount(other.adminId(), "{\"active\": false}").andExpect(status().isNotFound());
        patchAccount(staff.id(), "{\"roleId\": " + otherRole + "}").andExpect(status().isNotFound());
        asAdmin(post("/api/accounts/" + other.adminId() + "/unlock")).andExpect(status().isNotFound());
        asAdmin(post("/api/accounts/" + other.adminId() + "/reset-password")).andExpect(status().isNotFound());
    }

    @Test
    void 잠금을_풀면_바로_로그인할_수_있다() throws Exception {
        jdbc.update("UPDATE account SET locked_until = now() + interval '30 minutes', failed_login_count = 3 WHERE employee_id = ?",
                staff.id());
        login(staff.email(), TestFixture.PASSWORD).andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_LOCKED"));

        asAdmin(post("/api/accounts/" + staff.id() + "/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_type = 'ACCOUNT_UNLOCK' AND target_id = ?",
                Long.class, staff.id())).isEqualTo(1);
        login(staff.email(), TestFixture.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 비밀번호를_초기화하면_임시_비밀번호로_로그인해_변경_화면으로() throws Exception {
        String body = asAdmin(post("/api/accounts/" + staff.id() + "/reset-password"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String temp = body.replaceAll(".*\"temporaryPassword\":\"([^\"]+)\".*", "$1");
        assertThat(temp).hasSize(10).matches(PasswordRule.REGEX);

        login(staff.email(), TestFixture.PASSWORD).andExpect(status().isUnauthorized());
        login(staff.email(), temp).andExpect(jsonPath("$.data.next").value("CHANGE_PASSWORD"));
        // 감사 로그에 비밀번호는 없다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_type = 'ACCOUNT_PASSWORD_RESET' AND target_id = ? AND after_value IS NULL",
                Long.class, staff.id())).isEqualTo(1);
    }

    @Test
    void 직원_등록용_계정_생성은_기본_역할_직원과_임시_비밀번호() throws Exception {
        long typeId = jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '정규직'",
                Long.class, company.id());
        long employeeId = jdbc.queryForObject("""
                INSERT INTO employee (company_id, employee_no, name, email, hire_date, org_unit_id, employment_type_id)
                VALUES (?, ?, '신입', 'new.hire@test.example', CURRENT_DATE, ?, ?) RETURNING id""",
                Long.class, company.id(), employeeNoGenerator.next(company.id(), 2026), company.rootOrgUnitId(), typeId);

        String temp = accountService.createForEmployee(company.id(), employeeId, null);

        assertThat(temp).matches(PasswordRule.REGEX);
        assertThat(jdbc.queryForObject("""
                SELECT r.name FROM account a JOIN role r ON r.id = a.role_id WHERE a.employee_id = ? AND a.must_change_password""",
                String.class, employeeId)).isEqualTo("직원");
        login("new.hire@test.example", temp).andExpect(jsonPath("$.data.next").value("CHANGE_PASSWORD"));
    }
}
