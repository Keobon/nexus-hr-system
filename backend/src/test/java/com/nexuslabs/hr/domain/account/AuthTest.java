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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-AUTH-01 로그인 · F-AUTH-02 로그아웃 · F-AUTH-03 비밀번호 변경. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    TestFixture.Employee staff;

    @BeforeEach
    void setUp() {
        company = fixture.company("인증테스트");
        staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions changePassword(TestFixture.Employee e, String current, String next) throws Exception {
        return mvc.perform(patch("/api/auth/password").header("Authorization", fixture.token(e.id(), e.companyId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next)));
    }

    @Test
    void 로그인하면_토큰과_다음_화면을_준다() throws Exception {
        // 초기 설정 전 + COMPANY_MANAGE → 마법사
        login(company.adminEmail(), TestFixture.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.next").value("SETUP_WIZARD"));
        // 권한 없는 직원 → 홈
        String token = login(staff.email(), TestFixture.PASSWORD)
                .andExpect(jsonPath("$.data.next").value("HOME"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.employee.id").value(staff.id()));
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM account WHERE employee_id = ?",
                Boolean.class, staff.id())).isTrue();
    }

    @Test
    void 임시_비밀번호_계정은_비밀번호_변경_화면으로() throws Exception {
        TestFixture.Employee temp = fixture.employee(company.id(), company.rootOrgUnitId(), "최고 관리자", true);
        login(temp.email(), TestFixture.PASSWORD)
                .andExpect(jsonPath("$.data.next").value("CHANGE_PASSWORD"));
    }

    @Test
    void 설정이_끝난_회사의_관리자는_홈으로() throws Exception {
        jdbc.update("UPDATE company SET setup_completed = TRUE WHERE id = ?", company.id());
        login(company.adminEmail(), TestFixture.PASSWORD)
                .andExpect(jsonPath("$.data.next").value("HOME"));
    }

    @Test
    void 이메일은_대소문자와_앞뒤_공백을_무시한다() throws Exception {
        login("  " + staff.email().toUpperCase() + " ", TestFixture.PASSWORD)
                .andExpect(status().isOk());
    }

    @Test
    void 틀린_비밀번호와_없는_이메일은_같은_응답() throws Exception {
        login(staff.email(), "Wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 맞지 않습니다"));
        login("nobody@test.example", "Wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 맞지 않습니다"));
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM account WHERE employee_id = ?",
                Integer.class, staff.id())).isEqualTo(1);
    }

    @Test
    void 다섯_번_틀리면_30분_잠기고_맞는_비밀번호도_거부한다() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(staff.email(), "Wrong1234").andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        }
        login(staff.email(), "Wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.error.details.lockedUntil").value(startsWith("20")));
        login(staff.email(), TestFixture.PASSWORD)
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_LOCKED"));
        assertThat(jdbc.queryForObject(
                "SELECT locked_until BETWEEN now() + interval '29 minutes' AND now() + interval '31 minutes' FROM account WHERE employee_id = ?",
                Boolean.class, staff.id())).isTrue();

        // 잠금 시간이 지나면 로그인되고 실패 횟수는 0
        jdbc.update("UPDATE account SET locked_until = now() - interval '1 minute' WHERE employee_id = ?", staff.id());
        login(staff.email(), TestFixture.PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT failed_login_count, locked_until FROM account WHERE employee_id = ?", staff.id()))
                .containsEntry("failed_login_count", 0).containsEntry("locked_until", null);
    }

    @Test
    void 비활성_계정과_퇴직자는_로그인할_수_없다() throws Exception {
        jdbc.update("UPDATE account SET is_active = FALSE WHERE employee_id = ?", staff.id());
        login(staff.email(), TestFixture.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_INACTIVE"));

        TestFixture.Employee resigned = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", resigned.id());
        login(resigned.email(), TestFixture.PASSWORD)
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_INACTIVE"));
    }

    @Test
    void 로그아웃은_200() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", fixture.token(staff.id(), staff.companyId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 비밀번호를_바꾸면_변경_필요_상태가_풀리고_새_비밀번호로_로그인한다() throws Exception {
        TestFixture.Employee temp = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", true);
        // 변경 전에는 다른 API를 못 쓰지만 비밀번호 변경은 된다
        mvc.perform(get("/api/files/1").header("Authorization", fixture.token(temp.id(), temp.companyId())))
                .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"));
        changePassword(temp, TestFixture.PASSWORD, "NewPass123").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT must_change_password FROM account WHERE employee_id = ?",
                Boolean.class, temp.id())).isFalse();
        login(temp.email(), "NewPass123").andExpect(jsonPath("$.data.next").value("HOME"));
        login(temp.email(), TestFixture.PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호_변경_거부() throws Exception {
        changePassword(staff, "Wrong1234", "NewPass123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_WRONG_PASSWORD"));
        changePassword(staff, TestFixture.PASSWORD, TestFixture.PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        changePassword(staff, TestFixture.PASSWORD, "12345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.newPassword").exists());
    }
}
