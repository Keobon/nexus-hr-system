package com.nexuslabs.hr.domain.payroll;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-PAY-03·04 직원 급여 등록 · 조회 (API 설계서 10.2). 시계는 2030-03-04(월) — 회사 등록일 = 계산 변수 적용 시작일.
 * 직원은 TestFixture 기본값(입사일 2024-03-01, 급여 대상). JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class SalaryTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    TestFixture.Employee kim;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("급여등록테스트");
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return as(company.adminId(), request);
    }

    private ResultActions salary(long employeeId, String body) throws Exception {
        return admin(post("/api/employees/" + employeeId + "/salaries").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions assign(long employeeId, long itemId, long amount, String date) throws Exception {
        return admin(post("/api/employees/" + employeeId + "/pay-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payItemId\": %d, \"amount\": %d, \"effectiveFrom\": \"%s\", \"reason\": \"현장 배치\"}"
                        .formatted(itemId, amount, date)));
    }

    private long item(String body) throws Exception {
        String res = admin(post("/api/pay-items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    @Test
    void 연봉제는_적용_시작일에_유효한_분할_개월_수로_월_기본급을_정한다() throws Exception {
        // 회사 등록(3/4) 전 날짜는 가장 이른 값(12)을 쓴다
        salary(kim.id(), """
                {"salaryType": "ANNUAL", "annualSalary": 48000000, "monthlyBase": 1, "effectiveFrom": "2030-03-01", "reason": "연봉 계약"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.salaryType").value("ANNUAL"))
                .andExpect(jsonPath("$.data.annualSalary").value(48000000))
                .andExpect(jsonPath("$.data.monthlyBase").value(4000000))
                .andExpect(jsonPath("$.data.reason").value("연봉 계약"))
                .andExpect(jsonPath("$.data.createdByName").exists())
                .andExpect(jsonPath("$.data.warning").value(nullValue()));

        admin(post("/api/pay-variables").contentType(MediaType.APPLICATION_JSON)
                .content("{\"varCode\": \"ANNUAL_SPLIT_MONTHS\", \"value\": 13, \"effectiveFrom\": \"2031-01-01\"}"))
                .andExpect(status().isCreated());
        salary(kim.id(), """
                {"salaryType": "ANNUAL", "annualSalary": 50000000, "effectiveFrom": "2031-01-01", "reason": "2031 연봉"}""")
                .andExpect(jsonPath("$.data.monthlyBase").value(3846153));                         // ⌊5천만 ÷ 13⌋
        // 분할 개월 수를 같은 날짜로 정정하면(13 → 16) 그 뒤 등록부터 정정된 값을 쓴다
        admin(post("/api/pay-variables").contentType(MediaType.APPLICATION_JSON)
                .content("{\"varCode\": \"ANNUAL_SPLIT_MONTHS\", \"value\": 16, \"effectiveFrom\": \"2031-01-01\"}"))
                .andExpect(status().isCreated());
        salary(kim.id(), """
                {"salaryType": "ANNUAL", "annualSalary": 50000000, "effectiveFrom": "2031-01-01", "reason": "정정"}""")
                .andExpect(jsonPath("$.data.monthlyBase").value(3125000));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'EMPLOYEE_SALARY'",
                Long.class, company.id())).isEqualTo(3);
    }

    @Test
    void 월급제는_월_기본급만_받고_연봉은_비운다() throws Exception {
        salary(kim.id(), """
                {"salaryType": "MONTHLY", "annualSalary": 99, "monthlyBase": 3000000, "effectiveFrom": "2030-03-01", "reason": "입사"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.annualSalary").value(nullValue()))
                .andExpect(jsonPath("$.data.monthlyBase").value(3000000));
    }

    @Test
    void 대상_직원과_입력을_확인한다() throws Exception {
        salary(kim.id(), "{\"salaryType\": \"ANNUAL\", \"effectiveFrom\": \"2030-03-01\", \"reason\": \"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.annualSalary").exists());
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 0, \"effectiveFrom\": \"2030-03-01\", \"reason\": \"x\"}")
                .andExpect(jsonPath("$.error.fields.monthlyBase").exists());
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 1, \"effectiveFrom\": \"2024-02-29\", \"reason\": \"x\"}")
                .andExpect(jsonPath("$.error.fields.effectiveFrom").exists());              // 입사일 2024-03-01 전
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 1, \"effectiveFrom\": \"2030-03-01\", \"reason\": \" \"}")
                .andExpect(jsonPath("$.error.fields.reason").exists());

        String ok = "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 1, \"effectiveFrom\": \"2030-03-01\", \"reason\": \"x\"}";
        jdbc.update("UPDATE employee SET payroll_eligible = FALSE WHERE id = ?", kim.id());
        salary(kim.id(), ok).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        jdbc.update("UPDATE employee SET payroll_eligible = TRUE, status = 'RESIGNED' WHERE id = ?", kim.id());
        salary(kim.id(), ok).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));

        TestFixture.Company other = fixture.company("급여등록타사");
        TestFixture.Employee stranger = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false);
        salary(stranger.id(), ok).andExpect(status().isNotFound());
        admin(get("/api/employees/" + stranger.id() + "/salaries")).andExpect(status().isNotFound());
    }

    @Test
    void 정산된_월에_걸치는_소급_등록은_되고_경고를_준다() throws Exception {
        jdbc.update("""
                        INSERT INTO payroll_run (company_id, pay_month, pay_date, company_name_snap, ceo_name_snap,
                                                 business_reg_no_snap, company_address_snap, confirmed_by)
                        VALUES (?, '2030-02', DATE '2030-03-10', '회사', '대표', '000-00-00000', '서울', ?)
                        """,
                company.id(), company.adminId());
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 3000000, \"effectiveFrom\": \"2030-01-01\", \"reason\": \"소급\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.warning").value("PAY_MONTH_SETTLED"));
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 3100000, \"effectiveFrom\": \"2030-03-01\", \"reason\": \"인상\"}")
                .andExpect(jsonPath("$.data.warning").value(nullValue()));
    }

    @Test
    void 직원별_항목은_지정_직원_고정액_활성_항목만_등록한다() throws Exception {
        long hazard = item("{\"name\": \"위험수당\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"applyTo\": \"SELECTED\"}");
        assign(kim.id(), hazard, 150000, "2030-03-01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payItemId").value(hazard))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.reason").value("현장 배치"))
                .andExpect(jsonPath("$.data.warning").value(nullValue()));

        long meal = item("{\"name\": \"식대\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"defaultAmount\": 200000}");
        assign(kim.id(), meal, 1, "2030-03-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        assign(kim.id(), hazard, -1, "2030-03-01").andExpect(jsonPath("$.error.fields.amount").exists());
        jdbc.update("UPDATE pay_item SET is_active = FALSE WHERE id = ?", hazard);
        assign(kim.id(), hazard, 0, "2030-04-01").andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'EMPLOYEE_PAY_ITEM'",
                Long.class, company.id())).isEqualTo(1);
    }

    @Test
    void 직원_급여_상세는_오늘_유효한_값과_최신순_이력과_적용_항목을_보여_준다() throws Exception {
        item("{\"name\": \"식대\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"defaultAmount\": 200000, \"sortOrder\": 1}");
        item("{\"name\": \"직책수당\", \"itemKind\": \"EARNING\", \"calcMethod\": \"BASE_RATE\", \"baseRate\": 10, \"sortOrder\": 2}");
        long hazard = item("{\"name\": \"위험수당\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"applyTo\": \"SELECTED\", \"sortOrder\": 3}");
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 3000000, \"effectiveFrom\": \"2030-01-01\", \"reason\": \"입력\"}");
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 3333333, \"effectiveFrom\": \"2030-01-01\", \"reason\": \"정정\"}");
        salary(kim.id(), "{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 4000000, \"effectiveFrom\": \"2030-04-01\", \"reason\": \"인상 예정\"}");
        assign(kim.id(), hazard, 150000, "2030-02-01");
        assign(kim.id(), hazard, 0, "2030-05-01");

        admin(get("/api/employees/" + kim.id() + "/salaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payrollEligible").value(true))
                .andExpect(jsonPath("$.data.current.monthlyBase").value(3333333))           // 같은 날짜면 나중 행
                .andExpect(jsonPath("$.data.salaries[*].reason").value(contains("인상 예정", "정정", "입력")))
                .andExpect(jsonPath("$.data.payItems[*].name").value(contains("식대", "직책수당", "위험수당")))
                .andExpect(jsonPath("$.data.payItems[0].currentAmount").value(200000))
                .andExpect(jsonPath("$.data.payItems[1].currentAmount").value(333333))      // ⌊3,333,333 × 10%⌋
                .andExpect(jsonPath("$.data.payItems[2].applyTo").value("SELECTED"))
                .andExpect(jsonPath("$.data.payItems[2].currentAmount").value(150000))
                .andExpect(jsonPath("$.data.payItems[2].history[*].amount").value(contains(0, 150000)));

        // 본인 조회 — 같은 모양, 급여 대상이 아니면 비어 있다
        as(kim.id(), get("/api/me/salaries")).andExpect(jsonPath("$.data.current.monthlyBase").value(3333333));
        jdbc.update("UPDATE employee SET payroll_eligible = FALSE WHERE id = ?", kim.id());
        as(kim.id(), get("/api/me/salaries"))
                .andExpect(jsonPath("$.data.payrollEligible").value(false))
                .andExpect(jsonPath("$.data.current").value(nullValue()))
                .andExpect(jsonPath("$.data.salaries.length()").value(0));
    }

    @Test
    void 급여_목록은_급여_대상_재직_휴직자를_미등록까지_보여_주고_고정_지급_합계를_낸다() throws Exception {
        item("{\"name\": \"식대\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"defaultAmount\": 200000}");
        item("{\"name\": \"직책수당\", \"itemKind\": \"EARNING\", \"calcMethod\": \"BASE_RATE\", \"baseRate\": 10}");
        item("{\"name\": \"동호회비\", \"itemKind\": \"DEDUCTION\", \"calcMethod\": \"FIXED\", \"defaultAmount\": 10000}");
        long hazard = item("{\"name\": \"위험수당\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"applyTo\": \"SELECTED\"}");
        salary(kim.id(), "{\"salaryType\": \"ANNUAL\", \"annualSalary\": 36000000, \"effectiveFrom\": \"2030-01-01\", \"reason\": \"x\"}");
        assign(kim.id(), hazard, 150000, "2030-02-01");

        long team = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "현장팀");
        TestFixture.Employee lee = fixture.employee(company.id(), team, "직원", false);
        TestFixture.Employee notTarget = fixture.employee(company.id(), team, "직원", false);
        TestFixture.Employee resigned = fixture.employee(company.id(), team, "직원", false);
        jdbc.update("UPDATE employee SET payroll_eligible = FALSE WHERE id = ?", notTarget.id());
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", resigned.id());
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", lee.id());

        admin(get("/api/salaries")).andExpect(jsonPath("$.data.totalElements").value(3));      // 관리자 · kim · lee
        String kimNo = jdbc.queryForObject("SELECT employee_no FROM employee WHERE id = ?", String.class, kim.id());
        admin(get("/api/salaries?keyword=" + kimNo))
                .andExpect(jsonPath("$.data.content[0].registered").value(true))
                .andExpect(jsonPath("$.data.content[0].salaryType").value("ANNUAL"))
                .andExpect(jsonPath("$.data.content[0].annualSalary").value(36000000))
                .andExpect(jsonPath("$.data.content[0].monthlyBase").value(3000000))
                .andExpect(jsonPath("$.data.content[0].fixedItemsTotal").value(650000));   // 20만 + 30만 + 15만, 공제 제외
        admin(get("/api/salaries?orgUnitId=" + team))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].employeeId").value(lee.id()))
                .andExpect(jsonPath("$.data.content[0].registered").value(false))
                .andExpect(jsonPath("$.data.content[0].monthlyBase").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].fixedItemsTotal").value(nullValue()));
    }

    @Test
    void 급여_조회는_PAYROLL_READ_등록은_PAYROLL_MANAGE() throws Exception {
        TestFixture.Employee executive = fixture.employee(company.id(), company.rootOrgUnitId(), "경영진", false);
        as(kim.id(), get("/api/salaries")).andExpect(status().isForbidden());
        as(kim.id(), get("/api/employees/" + executive.id() + "/salaries")).andExpect(status().isForbidden());
        as(executive.id(), get("/api/salaries")).andExpect(status().isOk());
        as(executive.id(), get("/api/employees/" + kim.id() + "/salaries")).andExpect(status().isOk());
        as(executive.id(), post("/api/employees/" + kim.id() + "/salaries").contentType(MediaType.APPLICATION_JSON)
                .content("{\"salaryType\": \"MONTHLY\", \"monthlyBase\": 1, \"effectiveFrom\": \"2030-03-01\", \"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
        as(kim.id(), get("/api/me/salaries")).andExpect(status().isOk());
    }
}
