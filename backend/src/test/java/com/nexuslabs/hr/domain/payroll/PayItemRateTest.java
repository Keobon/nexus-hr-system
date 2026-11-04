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

import java.sql.Date;
import java.util.List;
import java.util.Map;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보험 요율 · 상한 · 하한의 적용 시작일과 이력(2026-11-04). 값은 추가만 하고, 정산은 귀속 월 말일에 유효한 값을 쓴다.
 * 시계는 2030-03-04(월) — 회사 등록일이 첫 요율 행의 적용 시작일이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class PayItemRateTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    long employeeId;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("보험요율테스트");
        employeeId = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false).id();
        jdbc.update("""
                INSERT INTO employee_salary (company_id, employee_id, salary_type, annual_salary, monthly_base,
                                             effective_from, reason, created_by)
                VALUES (?, ?, 'ANNUAL', 120000000, 10000000, '2029-01-01', '테스트', ?)
                """, company.id(), employeeId, company.adminId());
        // 이 테스트는 값 계산만 본다 — 근태가 없어 결근 공제가 붙지 않게 항목을 끈다
        jdbc.update("UPDATE pay_item SET is_active = FALSE WHERE company_id = ? AND name = '결근 공제'", company.id());
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long itemId(String name) {
        return jdbc.queryForObject("SELECT id FROM pay_item WHERE company_id = ? AND name = ?",
                Long.class, company.id(), name);
    }

    private String body(String effectiveFrom, long payItemId, String employeeRate, String companyRate, String upper,
                        String lower) {
        return """
                { "effectiveFrom": "%s", "items": [ { "payItemId": %d, "employeeRate": %s, "companyRate": %s,
                  "baseUpperLimit": %s, "baseLowerLimit": %s } ] }
                """.formatted(effectiveFrom, payItemId, employeeRate, companyRate, upper, lower);
    }

    /** 미리보기에서 그 직원의 항목 금액. 줄이 없으면 null. */
    private Number previewAmount(String month, String itemName) throws Exception {
        String res = admin(json(post("/api/payroll-runs/preview"), "{ \"payMonth\": \"%s\" }".formatted(month)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> found = JsonPath.read(res, "$.data.employees[?(@.employeeId == %d)].lines[?(@.name == '%s')].amount"
                .formatted(employeeId, itemName));
        return found.isEmpty() ? null : found.getFirst();
    }

    @Test
    void 새_회사는_2026년_요율과_국민연금_상한_하한을_받는다() throws Exception {
        admin(get("/api/pay-items")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '국민연금')].employeeRate").value(4.75))
                .andExpect(jsonPath("$.data[?(@.name == '국민연금')].baseUpperLimit").value(6590000))
                .andExpect(jsonPath("$.data[?(@.name == '국민연금')].baseLowerLimit").value(410000))
                .andExpect(jsonPath("$.data[?(@.name == '건강보험')].employeeRate").value(3.595))
                .andExpect(jsonPath("$.data[?(@.name == '장기요양보험')].employeeRate").value(0.4724))
                .andExpect(jsonPath("$.data[?(@.name == '고용보험')].companyRate").value(1.15));
    }

    @Test
    void 정산은_귀속_월_말일에_유효한_값을_쓴다() throws Exception {
        long pension = itemId("국민연금");
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-04-01", pension, "5", "5", "null", "null")))
                .andExpect(status().isCreated());
        // 3월 말일에는 처음 값(상한 659만 × 4.75%), 4월 말일부터는 새 값(1,000만 × 5%)
        assertThat(previewAmount("2030-03", "국민연금").longValue()).isEqualTo(313_025);
        assertThat(previewAmount("2030-04", "국민연금").longValue()).isEqualTo(500_000);
    }

    @Test
    void 미래_적용_값은_그_달이_오기_전까지_쓰이지_않는다() throws Exception {
        admin(json(post("/api/pay-items/insurance-rates"),
                body("2030-05-01", itemId("국민연금"), "5", "5", "null", "null"))).andExpect(status().isCreated());
        assertThat(previewAmount("2030-04", "국민연금").longValue()).isEqualTo(313_025);
        assertThat(previewAmount("2030-05", "국민연금").longValue()).isEqualTo(500_000);
        admin(get("/api/pay-items")).andExpect(jsonPath("$.data[?(@.name == '국민연금')].employeeRate").value(4.75));
    }

    @Test
    void 하한은_과세액이_낮을_때_기준을_올린다() throws Exception {
        admin(json(post("/api/pay-items/insurance-rates"),
                body("2030-03-10", itemId("국민연금"), "5", "5", "null", "20000000"))).andExpect(status().isCreated());
        assertThat(previewAmount("2030-03", "국민연금").longValue()).isEqualTo(1_000_000);
    }

    @Test
    void 첫_적용_시작일보다_이른_달은_가장_이른_값을_쓴다() throws Exception {
        // 회사 등록일(2030-03-04)보다 앞선 달
        assertThat(previewAmount("2030-01", "건강보험").longValue()).isEqualTo(359_500);
    }

    @Test
    void 같은_적용_시작일로_다시_넣으면_나중_값이_정정본이다() throws Exception {
        long pension = itemId("국민연금");
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-04-01", pension, "5", "5", "null", "null")));
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-04-01", pension, "6", "6", "null", "null")));
        assertThat(previewAmount("2030-04", "국민연금").longValue()).isEqualTo(600_000);
    }

    @Test
    void 정산된_달의_명세서는_요율이_바뀌어도_그대로다() throws Exception {
        admin(json(post("/api/payroll-runs"), "{ \"payMonth\": \"2030-02\" }")).andExpect(status().isCreated());
        Long before = jdbc.queryForObject("""
                SELECT l.amount FROM paystub_line l JOIN paystub s ON s.id = l.paystub_id
                WHERE s.company_id = ? AND s.employee_id = ? AND l.item_name_snap = '국민연금'
                """, Long.class, company.id(), employeeId);
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-01-01", itemId("국민연금"), "9", "9", "null", "null")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("""
                SELECT l.amount FROM paystub_line l JOIN paystub s ON s.id = l.paystub_id
                WHERE s.company_id = ? AND s.employee_id = ? AND l.item_name_snap = '국민연금'
                """, Long.class, company.id(), employeeId)).isEqualTo(before);
    }

    @Test
    void 일괄_저장_중_하나라도_틀리면_전부_저장하지_않는다() throws Exception {
        long pension = itemId("국민연금");
        long health = itemId("건강보험");
        Long before = jdbc.queryForObject("SELECT count(*) FROM pay_item_rate WHERE company_id = ?", Long.class,
                company.id());
        admin(json(post("/api/pay-items/insurance-rates"), """
                { "effectiveFrom": "2030-04-01", "items": [
                  { "payItemId": %d, "employeeRate": 5, "companyRate": 5 },
                  { "payItemId": %d, "employeeRate": 101, "companyRate": 5, "baseUpperLimit": 100, "baseLowerLimit": 200 } ] }
                """.formatted(pension, health)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['items[1].employeeRate']").exists())
                .andExpect(jsonPath("$.error.fields['items[1].baseLowerLimit']").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pay_item_rate WHERE company_id = ?", Long.class,
                company.id())).isEqualTo(before);
    }

    @Test
    void 요율_항목이_아니거나_중복이면_거부한다() throws Exception {
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-04-01", itemId("연장근로수당"), "5", "5", "null", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['items[0].payItemId']").exists());
        long pension = itemId("국민연금");
        admin(json(post("/api/pay-items/insurance-rates"), """
                { "effectiveFrom": "2030-04-01", "items": [
                  { "payItemId": %d, "employeeRate": 5, "companyRate": 5 },
                  { "payItemId": %d, "employeeRate": 6, "companyRate": 6 } ] }
                """.formatted(pension, pension)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['items[1].payItemId']").exists());
        admin(json(post("/api/pay-items/insurance-rates"), "{ \"items\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.effectiveFrom").exists())
                .andExpect(jsonPath("$.error.fields.items").exists());
    }

    @Test
    void 다른_회사_항목은_찾을_수_없다() throws Exception {
        TestFixture.Company other = fixture.company("보험요율다른회사");
        long otherItem = jdbc.queryForObject("SELECT id FROM pay_item WHERE company_id = ? AND name = '국민연금'",
                Long.class, other.id());
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-04-01", otherItem, "5", "5", "null", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['items[0].payItemId']").exists());
    }

    @Test
    void 조회는_현재_예정_이력을_나눠_준다() throws Exception {
        long pension = itemId("국민연금");
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-03-01", pension, "5", "5", "6000000", "null")));
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-05-01", pension, "6", "6", "null", "null")));
        admin(get("/api/pay-items/insurance-rates")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].payItemId").value(pension))
                .andExpect(jsonPath("$.data[0].name").value("국민연금"))
                .andExpect(jsonPath("$.data[0].current.employeeRate").value(4.75))
                .andExpect(jsonPath("$.data[0].current.effectiveFrom").value(MONDAY.toString()))
                .andExpect(jsonPath("$.data[0].upcoming[0].employeeRate").value(6))
                .andExpect(jsonPath("$.data[0].history.length()").value(2))
                .andExpect(jsonPath("$.data[0].history[1].baseUpperLimit").value(6000000))
                .andExpect(jsonPath("$.data[0].history[1].effectiveFrom").value("2030-03-01"));
    }

    @Test
    void 기존_PATCH로는_요율과_상한_하한을_바꿀_수_없다() throws Exception {
        long pension = itemId("국민연금");
        for (String field : List.of("employeeRate", "companyRate", "baseUpperLimit", "baseLowerLimit")) {
            admin(json(patch("/api/pay-items/" + pension), "{ \"%s\": 1 }".formatted(field)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields." + field).exists());
        }
        admin(json(patch("/api/pay-items/" + pension), "{ \"name\": \"국민연금(변경)\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeRate").value(4.75));
    }

    @Test
    void 요율_항목을_새로_만들면_첫_값이_저장된다() throws Exception {
        String res = admin(json(post("/api/pay-items"), """
                { "name": "산재보험", "itemKind": "DEDUCTION", "calcMethod": "TAXABLE_RATE", "employeeRate": 0,
                  "companyRate": 1.5, "baseUpperLimit": 5000000 }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.companyRate").value(1.5))
                .andExpect(jsonPath("$.data.baseUpperLimit").value(5000000))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.data.id")).longValue();
        Map<String, Object> row = jdbc.queryForMap("SELECT effective_from, created_by FROM pay_item_rate WHERE pay_item_id = ?", id);
        assertThat(row.get("effective_from")).isEqualTo(Date.valueOf(MONDAY));
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(company.adminId());
    }

    @Test
    void 저장하면_감사_로그를_남기고_권한이_없으면_거부한다() throws Exception {
        long pension = itemId("국민연금");
        admin(json(post("/api/pay-items/insurance-rates"), body("2030-04-01", pension, "5", "5", "null", "null")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'PAY_ITEM_RATE'", Long.class,
                company.id())).isEqualTo(1);
        mvc.perform(json(post("/api/pay-items/insurance-rates"), body("2030-04-01", pension, "5", "5", "null", "null"))
                        .header("Authorization", fixture.token(employeeId, company.id())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pay-items/insurance-rates").header("Authorization", fixture.token(employeeId, company.id())))
                .andExpect(status().isForbidden());
    }
}
