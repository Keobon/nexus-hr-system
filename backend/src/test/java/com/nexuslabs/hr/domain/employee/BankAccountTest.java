package com.nexuslabs.hr.domain.employee;

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

import java.util.List;
import java.util.Map;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-PAY-03 급여 계좌 (API 설계서 6장, BR-PAY-014). 계좌번호는 암호화 저장, 화면에는 뒤 4자리만.
 * 변경과 관리자의 전체 번호 조회는 감사 로그 — 로그에도 전체 번호는 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class BankAccountTest {

    private static final String ACCOUNT = "{\"bankName\": \" 국민은행 \", \"accountNo\": \"123-456-789012\", \"holder\": \"김직원\"}";

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
        company = fixture.company("계좌테스트");
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), company.id())));
    }

    private ResultActions save(TestFixture.Employee actor, String path, String body) throws Exception {
        return as(actor, put(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<Map<String, Object>> audits(String action) {
        return jdbc.queryForList("""
                        SELECT before_value::text AS before, after_value::text AS after FROM audit_log
                        WHERE company_id = ? AND target_type = 'BANK_ACCOUNT' AND action = ?::audit_action ORDER BY id
                        """,
                company.id(), action);
    }

    @Test
    void 관리자는_계좌를_암호화해_저장하고_전체_번호_조회는_감사_로그를_남긴다() throws Exception {
        as(hr, get("/api/employees/" + kim.id() + "/bank-account"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());   // 등록 전

        save(hr, "/api/employees/" + kim.id() + "/bank-account", ACCOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("국민은행"))
                .andExpect(jsonPath("$.data.accountNoMasked").value("***-****-9012"))
                .andExpect(jsonPath("$.data.accountNo").value(nullValue()))
                .andExpect(jsonPath("$.data.holder").value("김직원"));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT bank_account_enc, bank_account_last4 FROM employee WHERE id = ?", kim.id());
        assertThat((String) row.get("bank_account_enc")).doesNotContain("789012");
        assertThat(row.get("bank_account_last4")).isEqualTo("9012");

        as(hr, get("/api/employees/" + kim.id() + "/bank-account"))
                .andExpect(jsonPath("$.data.accountNo").value(nullValue()));
        assertThat(audits("VIEW")).isEmpty();
        as(hr, get("/api/employees/" + kim.id() + "/bank-account?reveal=true"))
                .andExpect(jsonPath("$.data.accountNo").value("123-456-789012"));
        assertThat(audits("VIEW")).hasSize(1);

        List<Map<String, Object>> updates = audits("UPDATE");
        assertThat(updates).hasSize(1);
        assertThat(updates.getFirst().get("before")).isNull();
        assertThat((String) updates.getFirst().get("after")).contains("***-****-9012").doesNotContain("789012");
    }

    @Test
    void 계좌번호는_숫자와_하이픈만_받는다() throws Exception {
        String path = "/api/employees/" + kim.id() + "/bank-account";
        save(hr, path, "{\"bankName\": \"국민은행\", \"accountNo\": \"123-45a-6789\", \"holder\": \"김직원\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.accountNo").exists());
        save(hr, path, "{\"bankName\": \"국민은행\", \"accountNo\": \"12-345\", \"holder\": \"김직원\"}")
                .andExpect(jsonPath("$.error.fields.accountNo").exists());                 // 숫자 5자리
        save(hr, path, "{\"bankName\": \"국민은행\", \"accountNo\": \"123--456789\", \"holder\": \"김직원\"}")
                .andExpect(jsonPath("$.error.fields.accountNo").exists());
        save(hr, path, "{\"bankName\": \"국민은행\", \"accountNo\": \"1234567890\"}")
                .andExpect(jsonPath("$.error.fields.holder").exists());
        assertThat(audits("UPDATE")).isEmpty();
    }

    @Test
    void 본인은_전체_번호를_보고_바꿀_수_있다() throws Exception {
        as(kim, get("/api/me/bank-account")).andExpect(jsonPath("$.data").doesNotExist());
        save(kim, "/api/me/bank-account", ACCOUNT).andExpect(status().isOk());
        save(kim, "/api/me/bank-account", "{\"bankName\": \"신한은행\", \"accountNo\": \"110-222-333444\", \"holder\": \"김직원\"}")
                .andExpect(jsonPath("$.data.accountNoMasked").value("***-****-3444"));
        as(kim, get("/api/me/bank-account?reveal=true"))
                .andExpect(jsonPath("$.data.bankName").value("신한은행"))
                .andExpect(jsonPath("$.data.accountNo").value("110-222-333444"));
        assertThat(audits("VIEW")).isEmpty();                                           // 본인 조회는 남기지 않는다
        List<Map<String, Object>> updates = audits("UPDATE");
        assertThat(updates).hasSize(2);
        assertThat((String) updates.get(1).get("before")).contains("***-****-9012");

        // 남의 계좌는 PAYROLL_MANAGE 만
        as(kim, get("/api/employees/" + hr.id() + "/bank-account")).andExpect(status().isForbidden());
        save(kim, "/api/employees/" + hr.id() + "/bank-account", ACCOUNT).andExpect(status().isForbidden());
    }

    @Test
    void 급여_대상이_아니어도_퇴직자여도_관리자는_계좌를_고칠_수_있다() throws Exception {
        String path = "/api/employees/" + kim.id() + "/bank-account";
        jdbc.update("UPDATE employee SET payroll_eligible = FALSE WHERE id = ?", kim.id());
        save(hr, path, ACCOUNT).andExpect(status().isOk());
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", kim.id());
        save(hr, path, ACCOUNT).andExpect(status().isOk());

        TestFixture.Company other = fixture.company("계좌타사");
        TestFixture.Employee stranger = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false);
        as(hr, get("/api/employees/" + stranger.id() + "/bank-account")).andExpect(status().isNotFound());
        save(hr, "/api/employees/" + stranger.id() + "/bank-account", ACCOUNT).andExpect(status().isNotFound());
    }
}
