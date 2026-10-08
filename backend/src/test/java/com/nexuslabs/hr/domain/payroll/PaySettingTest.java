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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-PAY-01·02 급여 항목 · 계산 변수 · 세율 구간 (API 설계서 10.1). 회사 등록 기본값(4대보험 4 · 근태 연동 5 · 출장비 1,
 * 계산 변수 5, 세율 구간 5) 위에서 시작한다. 시계는 2030-03-04(월) — 회사 등록일 = 계산 변수의 적용 시작일.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class PaySettingTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("급여설정테스트");
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return as(company.adminId(), request);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long itemId(String name) {
        return jdbc.queryForObject("SELECT id FROM pay_item WHERE company_id = ? AND name = ?",
                Long.class, company.id(), name);
    }

    private long createItem(String body) throws Exception {
        String res = admin(json(post("/api/pay-items"), body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private long audits(String target) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = ?",
                Long.class, company.id(), target);
    }

    // ---- 급여 항목 ----

    @Test
    void 기본_항목이_정렬_순서대로_나오고_요율은_끝의_0을_뗀다() throws Exception {
        admin(get("/api/pay-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[0].name").value("연장근로수당"))
                .andExpect(jsonPath("$.data[0].multiplier").value(1.5))
                .andExpect(jsonPath("$.data[0].isTaxable").value(true))
                .andExpect(jsonPath("$.data[5].name").value("출장비 정산"))
                .andExpect(jsonPath("$.data[5].isTaxable").value(false))
                .andExpect(jsonPath("$.data[6].name").value("국민연금"))
                .andExpect(jsonPath("$.data[6].employeeRate").value(4.5))
                .andExpect(jsonPath("$.data[6].defaultAmount").value(nullValue()))
                .andExpect(jsonPath("$.data[6].inUse").value(false))
                .andExpect(jsonPath("$.data[9].companyRate").value(1.15));
    }

    @Test
    void 계산_방식별로_필요한_값만_저장하고_해당_없는_값은_비운다() throws Exception {
        admin(json(post("/api/pay-items"), """
                { "name": "식대", "itemKind": "EARNING", "isTaxable": false, "nonTaxableLimit": 200000, "calcMethod": "FIXED",
                  "defaultAmount": 250000, "inOrdinaryWage": true, "baseRate": 3, "multiplier": 2, "attendanceBasis": "NIGHT" }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.applyTo").value("ALL"))
                .andExpect(jsonPath("$.data.defaultAmount").value(250000))
                .andExpect(jsonPath("$.data.nonTaxableLimit").value(200000))
                .andExpect(jsonPath("$.data.inOrdinaryWage").value(true))
                .andExpect(jsonPath("$.data.baseRate").value(nullValue()))
                .andExpect(jsonPath("$.data.multiplier").value(nullValue()))
                .andExpect(jsonPath("$.data.attendanceBasis").value(nullValue()))
                .andExpect(jsonPath("$.data.sortOrder").value(105))
                .andExpect(jsonPath("$.data.isActive").value(true));
        // 공제는 항상 과세, 통상임금 · 비과세 한도는 쓸 수 없다
        admin(json(post("/api/pay-items"), """
                { "name": "사우회비", "itemKind": "DEDUCTION", "calcMethod": "BASE_RATE", "baseRate": 1.25,
                  "isTaxable": false, "nonTaxableLimit": 1000, "inOrdinaryWage": true }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.baseRate").value(1.25))
                .andExpect(jsonPath("$.data.isTaxable").value(true))
                .andExpect(jsonPath("$.data.nonTaxableLimit").value(nullValue()))
                .andExpect(jsonPath("$.data.inOrdinaryWage").value(false));
        admin(json(post("/api/pay-items"), """
                { "name": "위험수당", "itemKind": "EARNING", "calcMethod": "FIXED", "applyTo": "SELECTED", "defaultAmount": 1 }
                """))
                .andExpect(jsonPath("$.data.applyTo").value("SELECTED"))
                .andExpect(jsonPath("$.data.defaultAmount").value(nullValue()));
        assertThat(audits("PAY_ITEM")).isEqualTo(3);
    }

    @Test
    void 계산_방식에_맞지_않는_입력은_필드별로_거부한다() throws Exception {
        admin(json(post("/api/pay-items"), """
                { "name": "직책수당", "itemKind": "EARNING", "calcMethod": "BASE_RATE", "applyTo": "SELECTED", "baseRate": 0 }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.applyTo").exists())
                .andExpect(jsonPath("$.error.fields.baseRate").exists());
        admin(json(post("/api/pay-items"), """
                { "name": "보험", "itemKind": "EARNING", "calcMethod": "TAXABLE_RATE", "employeeRate": 101,
                  "baseUpperLimit": 100, "baseLowerLimit": 200 }
                """))
                .andExpect(jsonPath("$.error.fields.itemKind").exists())
                .andExpect(jsonPath("$.error.fields.employeeRate").exists())
                .andExpect(jsonPath("$.error.fields.companyRate").exists())
                .andExpect(jsonPath("$.error.fields.baseLowerLimit").exists());
        admin(json(post("/api/pay-items"), """
                { "name": "지각 공제", "itemKind": "EARNING", "calcMethod": "ATTENDANCE", "attendanceBasis": "ABSENCE", "multiplier": 1.234 }
                """))
                .andExpect(jsonPath("$.error.fields.itemKind").exists())
                .andExpect(jsonPath("$.error.fields.multiplier").exists());
        admin(json(post("/api/pay-items"), """
                { "name": "식대", "itemKind": "EARNING", "calcMethod": "FIXED" }
                """))
                .andExpect(jsonPath("$.error.fields.defaultAmount").exists());
        admin(json(post("/api/pay-items"), """
                { "name": "국민연금", "itemKind": "EARNING", "calcMethod": "MANUAL" }
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
    }

    @Test
    void 근거_시간마다_출장_경비는_회사에_활성_항목_하나() throws Exception {
        admin(json(post("/api/pay-items"), """
                { "name": "연장수당2", "itemKind": "EARNING", "calcMethod": "ATTENDANCE", "attendanceBasis": "OVERTIME", "multiplier": 2 }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        admin(json(post("/api/pay-items"), """
                { "name": "출장비2", "itemKind": "EARNING", "calcMethod": "TRIP_EXPENSE" }
                """))
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        // 기존 것을 끄면 새로 만들 수 있고, 꺼 둔 것을 다시 켜면 거부
        long overtime = itemId("연장근로수당");
        admin(delete("/api/pay-items/" + overtime)).andExpect(jsonPath("$.data.result").value("DELETED"));
        createItem("""
                { "name": "연장수당2", "itemKind": "EARNING", "calcMethod": "ATTENDANCE", "attendanceBasis": "OVERTIME", "multiplier": 2 }
                """);
        long night = itemId("야간근로수당");
        admin(json(patch("/api/pay-items/" + night), "{\"isActive\": false}")).andExpect(status().isOk());
        createItem("""
                { "name": "야간수당2", "itemKind": "EARNING", "calcMethod": "ATTENDANCE", "attendanceBasis": "NIGHT", "multiplier": 0.5 }
                """);
        admin(json(patch("/api/pay-items/" + night), "{\"isActive\": true}"))
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void 수정은_보낸_필드만_바꾸고_쓰인_항목은_구분_방식_대상을_못_바꾸고_삭제하면_비활성화() throws Exception {
        long id = createItem("""
                { "name": "위험수당", "itemKind": "EARNING", "calcMethod": "FIXED", "applyTo": "SELECTED" }
                """);
        admin(json(patch("/api/pay-items/" + id), "{\"name\": \"현장수당\", \"sortOrder\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("현장수당"))
                .andExpect(jsonPath("$.data.applyTo").value("SELECTED"))
                .andExpect(jsonPath("$.data.sortOrder").value(7));
        admin(json(patch("/api/pay-items/" + id), "{\"name\": null, \"color\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.color").exists());

        // 직원별 금액 이력이 생기면 "쓰인 항목"
        admin(json(post("/api/employees/" + company.adminId() + "/pay-items"), """
                { "payItemId": %d, "amount": 100000, "effectiveFrom": "2030-03-04" }""".formatted(id)))
                .andExpect(status().isCreated());
        admin(get("/api/pay-items")).andExpect(jsonPath("$.data[?(@.id == %d)].inUse".formatted(id)).value(true));
        admin(json(patch("/api/pay-items/" + id), "{\"applyTo\": \"ALL\", \"defaultAmount\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_IN_USE"));
        admin(json(patch("/api/pay-items/" + id), "{\"name\": \"위험수당\"}")).andExpect(status().isOk());
        admin(delete("/api/pay-items/" + id)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        admin(get("/api/pay-items?activeOnly=true")).andExpect(jsonPath("$.data[*].name").value(not(hasItem("위험수당"))));
        assertThat(audits("PAY_ITEM")).isEqualTo(4);                                  // 등록 · 수정 2번 · 비활성화
    }

    @Test
    void 조회는_급여_권한이_있어야_하고_관리는_PAYROLL_MANAGE_만() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        TestFixture.Employee executive = fixture.employee(company.id(), company.rootOrgUnitId(), "경영진", false);
        as(staff.id(), get("/api/pay-items")).andExpect(status().isForbidden());
        as(executive.id(), get("/api/pay-items")).andExpect(status().isOk());
        as(executive.id(), json(post("/api/pay-items"), "{\"name\": \"x\", \"itemKind\": \"EARNING\", \"calcMethod\": \"MANUAL\"}"))
                .andExpect(status().isForbidden());
        as(executive.id(), get("/api/pay-variables")).andExpect(status().isForbidden());
        TestFixture.Company other = fixture.company("급여설정타사");
        long otherItem = jdbc.queryForObject("SELECT id FROM pay_item WHERE company_id = ? AND name = '국민연금'",
                Long.class, other.id());
        admin(json(patch("/api/pay-items/" + otherItem), "{\"sortOrder\": 1}")).andExpect(status().isNotFound());
    }

    // ---- 계산 변수 ----

    @Test
    void 계산_변수는_현재_예정_이력으로_나뉘고_같은_날짜로_다시_넣으면_정정본이_된다() throws Exception {
        admin(get("/api/pay-variables"))
                .andExpect(jsonPath("$.data[*].varCode").value(contains("DEPENDENT_DEDUCTION", "MULTI_CHILD_DEDUCTION",
                        "LOCAL_TAX_RATE", "ANNUAL_SPLIT_MONTHS", "MONTHLY_STANDARD_HOURS")))
                .andExpect(jsonPath("$.data[2].current.value").value(10))
                .andExpect(jsonPath("$.data[2].current.effectiveFrom").value("2030-03-04"))
                .andExpect(jsonPath("$.data[2].upcoming.length()").value(0));

        admin(json(post("/api/pay-variables"), "{\"varCode\": \"LOCAL_TAX_RATE\", \"value\": 11.5, \"effectiveFrom\": \"2031-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.varCode").value("LOCAL_TAX_RATE"))
                .andExpect(jsonPath("$.data.current.value").value(10))
                .andExpect(jsonPath("$.data.upcoming[0].value").value(11.5))
                .andExpect(jsonPath("$.data.upcoming[0].effectiveFrom").value("2031-01-01"));
        admin(json(post("/api/pay-variables"), "{\"varCode\": \"LOCAL_TAX_RATE\", \"value\": 9, \"effectiveFrom\": \"2030-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.history[*].effectiveFrom").value(contains("2030-03-04", "2030-01-01")));
        // 잘못 넣은 미래 값은 같은 날짜로 다시 넣어 고친다 — 앞 행은 superseded
        admin(json(post("/api/pay-variables"), "{\"varCode\": \"LOCAL_TAX_RATE\", \"value\": 12, \"effectiveFrom\": \"2031-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.upcoming[*].value").value(contains(11.5, 12)))
                .andExpect(jsonPath("$.data.upcoming[*].superseded").value(contains(true, false)));
        admin(json(post("/api/pay-variables"), "{\"varCode\": \"LOCAL_TAX_RATE\", \"value\": 10.5, \"effectiveFrom\": \"2030-03-04\"}"))
                .andExpect(jsonPath("$.data.current.value").value(10.5))
                .andExpect(jsonPath("$.data.current.superseded").value(false))
                .andExpect(jsonPath("$.data.history[*].value").value(contains(10.5, 10, 9)))
                .andExpect(jsonPath("$.data.history[*].superseded").value(contains(false, true, false)));
        assertThat(audits("PAY_VARIABLE")).isEqualTo(4);
    }

    @Test
    void 계산_변수는_변수마다_값_범위를_검사한다() throws Exception {
        for (String bad : new String[]{"{\"varCode\": \"ANNUAL_SPLIT_MONTHS\", \"value\": 12.5",
                "{\"varCode\": \"ANNUAL_SPLIT_MONTHS\", \"value\": 0", "{\"varCode\": \"MONTHLY_STANDARD_HOURS\", \"value\": 745",
                "{\"varCode\": \"LOCAL_TAX_RATE\", \"value\": 100.5", "{\"varCode\": \"DEPENDENT_DEDUCTION\", \"value\": 1.5"}) {
            admin(json(post("/api/pay-variables"), bad + ", \"effectiveFrom\": \"2031-01-01\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.value").exists());
        }
        admin(json(post("/api/pay-variables"), "{\"varCode\": \"UNKNOWN\", \"value\": 1, \"effectiveFrom\": \"2031-01-01\"}"))
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
    }

    // ---- 세율 구간 ----

    @Test
    void 세율_구간은_전체_교체하고_빈틈이나_겹침은_거부한다() throws Exception {
        admin(get("/api/tax-brackets"))
                .andExpect(jsonPath("$.data.brackets.length()").value(5))
                .andExpect(jsonPath("$.data.brackets[0].rate").value(6))
                .andExpect(jsonPath("$.data.brackets[4].upperBound").value(nullValue()));

        String ok = """
                { "brackets": [ { "lowerBound": 3000000, "upperBound": null, "rate": 20.5, "progressiveDeduction": 300000 },
                                { "lowerBound": 0, "upperBound": 3000000, "rate": 10, "progressiveDeduction": 0 } ] }""";
        admin(json(put("/api/tax-brackets"), ok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brackets.length()").value(2))
                .andExpect(jsonPath("$.data.brackets[1].rate").value(20.5));
        assertThat(audits("TAX_BRACKET")).isEqualTo(1);

        for (String bad : new String[]{
                "[{\"lowerBound\": 100, \"upperBound\": null, \"rate\": 10, \"progressiveDeduction\": 0}]",
                "[{\"lowerBound\": 0, \"upperBound\": 100, \"rate\": 10, \"progressiveDeduction\": 0},"
                        + "{\"lowerBound\": 200, \"upperBound\": null, \"rate\": 20, \"progressiveDeduction\": 0}]",
                "[{\"lowerBound\": 0, \"upperBound\": 100, \"rate\": 10, \"progressiveDeduction\": 0}]",
                "[{\"lowerBound\": 0, \"upperBound\": null, \"rate\": 101, \"progressiveDeduction\": 0}]"}) {
            admin(json(put("/api/tax-brackets"), "{\"brackets\": " + bad + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("TAX_BRACKET_INVALID"));
        }
        admin(get("/api/tax-brackets")).andExpect(jsonPath("$.data.brackets.length()").value(2));
    }
}
