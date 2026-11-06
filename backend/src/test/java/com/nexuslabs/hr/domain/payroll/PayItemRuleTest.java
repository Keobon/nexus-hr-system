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

import java.util.List;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 고정액 항목의 적용 대상 — 부서 · 직급 · 직원(2026-11-06). 직원 > 직급 > 부서 > 항목 기본 금액 중 하나만 쓰고,
 * 귀속 월 말일에 유효한 규칙을 쓴다. 조직: 회사 › 개발본부 › 백엔드팀. 시계는 2030-03-04(월).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class PayItemRuleTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    long cid, division, team;
    long staffInTeam, managerInTeam, deputyAtRoot, staffAtRoot;
    long meal, positionPay, club, bonus;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("적용대상테스트");
        cid = company.id();
        division = fixture.orgUnit(cid, company.rootOrgUnitId(), "개발본부");
        team = fixture.orgUnit(cid, division, "백엔드팀");
        staffInTeam = employee(team, "사원");
        managerInTeam = employee(team, "과장");
        deputyAtRoot = employee(company.rootOrgUnitId(), "차장");
        staffAtRoot = employee(company.rootOrgUnitId(), "사원");
        // 근태 없이 계산하므로 결근 공제를 끈다
        jdbc.update("UPDATE pay_item SET is_active = FALSE WHERE company_id = ? AND name = '결근 공제'", cid);
        meal = item("{\"name\": \"식대\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"defaultAmount\": 200000}");
        positionPay = item("{\"name\": \"직책수당\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"applyTo\": \"SELECTED\"}");
        club = item("{\"name\": \"동호회비\", \"itemKind\": \"DEDUCTION\", \"calcMethod\": \"FIXED\", \"defaultAmount\": 10000}");
        bonus = item("{\"name\": \"성과급\", \"itemKind\": \"EARNING\", \"calcMethod\": \"MANUAL\"}");
    }

    private long employee(long orgUnitId, String grade) {
        long id = fixture.employee(cid, orgUnitId, "직원", false).id();
        jdbc.update("UPDATE employee SET job_grade_id = (SELECT id FROM job_grade WHERE company_id = ? AND name = ?) WHERE id = ?",
                cid, grade, id);
        jdbc.update("""
                INSERT INTO employee_salary (company_id, employee_id, salary_type, monthly_base, effective_from, reason, created_by)
                VALUES (?, ?, 'MONTHLY', 3000000, DATE '2024-03-01', '등록', ?)""", cid, id, company.adminId());
        return id;
    }

    private long gradeId(String name) {
        return jdbc.queryForObject("SELECT id FROM job_grade WHERE company_id = ? AND name = ?", Long.class, cid, name);
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), cid)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long item(String body) throws Exception {
        String res = admin(json(post("/api/pay-items"), body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private ResultActions orgRule(long itemId, long orgUnitId, boolean includeSub, String amount, String from) throws Exception {
        return admin(json(post("/api/pay-items/" + itemId + "/rules"), """
                {"effectiveFrom": "%s", "targetType": "ORG_UNIT", "orgUnitId": %d, "includeSubOrgs": %s, "amount": %s}"""
                .formatted(from, orgUnitId, includeSub, amount)));
    }

    private ResultActions gradeRule(long itemId, String grade, boolean orAbove, String amount, String from) throws Exception {
        return admin(json(post("/api/pay-items/" + itemId + "/rules"), """
                {"effectiveFrom": "%s", "targetType": "JOB_GRADE", "jobGradeId": %d, "gradeOrAbove": %s, "amount": %s}"""
                .formatted(from, gradeId(grade), orAbove, amount)));
    }

    private ResultActions personal(long employeeId, long itemId, String amount, String from) throws Exception {
        return admin(json(post("/api/employees/" + employeeId + "/pay-items"), """
                {"payItemId": %d, "amount": %s, "effectiveFrom": "%s", "reason": "개별"}""".formatted(itemId, amount, from)));
    }

    private String preview(String month) throws Exception {
        return admin(json(post("/api/payroll-runs/preview"), "{\"payMonth\": \"%s\"}".formatted(month)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** 그 직원 명세서 줄 [금액, 산식]. 줄이 없으면 null. */
    private Object[] line(String preview, long employeeId, String itemName) {
        List<Number> amounts = JsonPath.read(preview,
                "$.data.employees[?(@.employeeId == %d)].lines[?(@.name == '%s')].amount".formatted(employeeId, itemName));
        if (amounts.isEmpty()) {
            return null;
        }
        List<String> notes = JsonPath.read(preview,
                "$.data.employees[?(@.employeeId == %d)].lines[?(@.name == '%s')].formulaNote".formatted(employeeId, itemName));
        return new Object[]{amounts.getFirst().longValue(), notes.getFirst()};
    }

    @Test
    void 직원_직급_부서_기본_순서로_하나만_적용한다() throws Exception {
        orgRule(meal, division, true, "250000", "2030-01-01").andExpect(status().isCreated());
        gradeRule(meal, "과장", true, "300000", "2030-01-01").andExpect(status().isCreated());
        personal(staffAtRoot, meal, "0", "2030-01-01").andExpect(status().isCreated());

        String p = preview("2030-03");
        assertThat(line(p, staffInTeam, "식대")).containsExactly(250_000L, "개발본부 규칙");     // 부서(하위 포함)
        assertThat(line(p, managerInTeam, "식대")).containsExactly(300_000L, "과장 이상 규칙");  // 직급이 부서보다 먼저
        assertThat(line(p, deputyAtRoot, "식대")).containsExactly(300_000L, "과장 이상 규칙");   // 차장도 과장 이상
        assertThat(line(p, staffAtRoot, "식대")).isNull();                                     // 개별 0원 = 제외
    }

    @Test
    void 기본_금액은_규칙이_없는_직원에게_쓴다() throws Exception {
        String p = preview("2030-03");
        assertThat(line(p, staffAtRoot, "식대")).containsExactly(200_000L, null);
    }

    @Test
    void 가까운_부서가_이기고_하위_미포함_규칙은_그_부서만_맞는다() throws Exception {
        orgRule(meal, division, true, "250000", "2030-01-01");
        orgRule(meal, team, false, "220000", "2030-01-01");
        assertThat(line(preview("2030-03"), staffInTeam, "식대")).containsExactly(220_000L, "백엔드팀 규칙");

        orgRule(club, division, false, "5000", "2030-01-01");
        // 팀 직원은 본부의 "하위 미포함" 규칙에 맞지 않아 기본 금액
        assertThat(line(preview("2030-03"), staffInTeam, "동호회비")).containsExactly(10_000L, null);
    }

    @Test
    void 그_직급만_규칙이_먼저이고_이상끼리는_기준이_높은_규칙() throws Exception {
        gradeRule(meal, "대리", true, "310000", "2030-01-01");
        gradeRule(meal, "과장", false, "280000", "2030-01-01");
        gradeRule(meal, "차장", true, "400000", "2030-01-01");
        String p = preview("2030-03");
        assertThat(line(p, managerInTeam, "식대")).containsExactly(280_000L, "과장 규칙");
        assertThat(line(p, deputyAtRoot, "식대")).containsExactly(400_000L, "차장 이상 규칙");
        assertThat(line(p, staffInTeam, "식대")).containsExactly(200_000L, null);
    }

    @Test
    void 지정_직원_항목은_규칙에_맞는_직원만_받고_공제도_같다() throws Exception {
        gradeRule(positionPay, "과장", true, "150000", "2030-01-01").andExpect(status().isCreated());
        orgRule(club, team, false, "5000", "2030-01-01").andExpect(status().isCreated());
        String p = preview("2030-03");
        assertThat(line(p, managerInTeam, "직책수당")).containsExactly(150_000L, "과장 이상 규칙");
        assertThat(line(p, staffInTeam, "직책수당")).isNull();
        assertThat(line(p, staffInTeam, "동호회비")).containsExactly(5_000L, "백엔드팀 규칙");
        assertThat(line(p, staffAtRoot, "동호회비")).containsExactly(10_000L, null);
    }

    @Test
    void 귀속_월_말일에_유효한_규칙을_쓰고_해제하면_다시_기본이다() throws Exception {
        orgRule(meal, division, true, "250000", "2030-04-01");
        orgRule(meal, division, true, "null", "2030-05-01");
        assertThat(line(preview("2030-03"), staffInTeam, "식대")).containsExactly(200_000L, null);
        assertThat(line(preview("2030-04"), staffInTeam, "식대")).containsExactly(250_000L, "개발본부 규칙");
        assertThat(line(preview("2030-05"), staffInTeam, "식대")).containsExactly(200_000L, null);

        // 같은 날짜로 다시 넣으면 나중 값이 정정본
        orgRule(meal, division, true, "260000", "2030-04-01");
        assertThat(line(preview("2030-04"), staffInTeam, "식대")).containsExactly(260_000L, "개발본부 규칙");

        // 개별 금액도 해제하면 규칙으로 돌아간다
        personal(staffInTeam, meal, "0", "2030-04-01").andExpect(status().isCreated());
        personal(staffInTeam, meal, "null", "2030-04-02").andExpect(status().isCreated());
        assertThat(line(preview("2030-04"), staffInTeam, "식대")).containsExactly(260_000L, "개발본부 규칙");
    }

    @Test
    void 발령으로_직급이_바뀌면_다음_정산부터_맞는_금액이_붙는다() throws Exception {
        gradeRule(positionPay, "과장", true, "150000", "2030-01-01");
        assertThat(line(preview("2030-03"), staffInTeam, "직책수당")).isNull();
        admin(json(post("/api/assignments"), """
                {"employeeId": %d, "assignmentType": "PROMOTION", "toOrgUnitId": %d, "toJobGradeId": %d, "reason": "승진"}"""
                .formatted(staffInTeam, team, gradeId("과장")))).andExpect(status().isCreated());
        assertThat(line(preview("2030-03"), staffInTeam, "직책수당")).containsExactly(150_000L, "과장 이상 규칙");
    }

    @Test
    void 고정액이_아니거나_대상이_틀리면_거부한다() throws Exception {
        admin(json(post("/api/pay-items/" + bonus + "/rules"), """
                {"effectiveFrom": "2030-01-01", "targetType": "ORG_UNIT", "orgUnitId": %d, "amount": 1}""".formatted(team)))
                .andExpect(status().isBadRequest());
        admin(json(post("/api/pay-items/" + meal + "/rules"), """
                {"effectiveFrom": "2030-01-01", "targetType": "ORG_UNIT", "amount": 1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.orgUnitId").exists());
        TestFixture.Company other = fixture.company("적용대상다른회사");
        long otherOrg = fixture.orgUnit(other.id(), other.rootOrgUnitId(), "남의부서");
        orgRule(meal, otherOrg, true, "1", "2030-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.orgUnitId").exists());
        orgRule(meal, team, true, "-1", "2030-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.amount").exists());
        // 직원별 금액도 고정액 항목만
        personal(staffInTeam, bonus, "1", "2030-01-01").andExpect(status().isBadRequest());
        // 권한
        mvc.perform(json(post("/api/pay-items/" + meal + "/rules"), "{}")
                        .header("Authorization", fixture.token(staffInTeam, cid)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 규칙에만_쓰인_부서와_직급은_지우지_않고_비활성화한다() throws Exception {
        long empty = fixture.orgUnit(cid, division, "빈팀");
        jdbc.update("INSERT INTO job_grade (company_id, name, sort_order) VALUES (?, '수석', 99)", cid);
        orgRule(meal, empty, true, "1", "2030-01-01").andExpect(status().isCreated());
        gradeRule(meal, "수석", false, "1", "2030-01-01").andExpect(status().isCreated());

        admin(delete("/api/org-units/" + empty)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        admin(delete("/api/job-grades/" + gradeId("수석"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
    }

    @Test
    void 규칙_목록과_감사_로그_직원_급여의_출처() throws Exception {
        orgRule(meal, division, true, "250000", "2030-01-01");
        orgRule(meal, division, true, "270000", "2030-05-01");
        gradeRule(meal, "과장", true, "300000", "2030-01-01");
        admin(get("/api/pay-items/" + meal + "/rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.targetType == 'ORG_UNIT')].orgUnitName").value("개발본부"))
                .andExpect(jsonPath("$.data[?(@.targetType == 'ORG_UNIT')].current.amount").value(250000))
                .andExpect(jsonPath("$.data[?(@.targetType == 'ORG_UNIT')].upcoming[0].amount").value(270000))
                .andExpect(jsonPath("$.data[?(@.targetType == 'JOB_GRADE')].jobGradeName").value("과장"))
                .andExpect(jsonPath("$.data[?(@.targetType == 'JOB_GRADE')].gradeOrAbove").value(true));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'PAY_ITEM_RULE'", Long.class, cid))
                .isEqualTo(3);

        String line = "$.data.payItems[?(@.payItemId == %d)]".formatted(meal);
        admin(get("/api/employees/" + staffInTeam + "/salaries")).andExpect(status().isOk())
                .andExpect(jsonPath(line + ".currentAmount").value(250000))
                .andExpect(jsonPath(line + ".source.type").value("ORG_UNIT"))
                .andExpect(jsonPath(line + ".source.name").value("개발본부"));
        admin(get("/api/employees/" + staffAtRoot + "/salaries"))
                .andExpect(jsonPath(line + ".currentAmount").value(200000))
                .andExpect(jsonPath(line + ".source.type").value("DEFAULT"));
        personal(staffAtRoot, meal, "0", "2030-03-01");
        admin(get("/api/employees/" + staffAtRoot + "/salaries"))
                .andExpect(jsonPath(line + ".currentAmount").value(0))
                .andExpect(jsonPath(line + ".source.type").value("EMPLOYEE"));
    }
}
