package com.nexuslabs.hr.domain.payroll;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.domain.payroll.service.PayslipService;
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

import java.time.LocalDateTime;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-PAY-05·06·07 월 급여 정산 · 명세서 · 인건비 (API 설계서 10.3). 귀속 월은 2030-03.
 * <ul>
 *   <li>김 — 월 300만, 식대 20만(통상임금 · 비과세 한도 20만), 3/4 승인 연장 120분, 3/28–29 결근, 출장 경비 59,900, 가족 3명(자녀 2)</li>
 *   <li>이 — 3/11 입사(재직 21일), 월 310만 · 박 — 3/21 퇴직 발효(재직 20일), 월 310만</li>
 *   <li>최 · 관리자 — 급여 미등록 / 정 — 급여 대상 아님</li>
 * </ul>
 * 결근은 max(입사일, 직원 등록일)부터 판정하므로 등록일(created_at)로 결근 날짜를 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class PayrollTest {

    private static final String MARCH = "{\"payMonth\": \"2030-03\"%s}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;
    @Autowired PayslipService payslipService;

    TestFixture.Company company;
    TestFixture.Employee kim, lee, park, choi;
    long team, bonus;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("급여정산테스트");
        long cid = company.id();
        team = fixture.orgUnit(cid, company.rootOrgUnitId(), "개발팀");
        kim = fixture.employee(cid, team, "직원", false);
        lee = fixture.employee(cid, team, "직원", false);
        park = fixture.employee(cid, company.rootOrgUnitId(), "직원", false);
        choi = fixture.employee(cid, company.rootOrgUnitId(), "직원", false);
        TestFixture.Employee jung = fixture.employee(cid, company.rootOrgUnitId(), "직원", false);
        jdbc.update("UPDATE employee SET payroll_eligible = FALSE WHERE id = ?", jung.id());

        jdbc.update("UPDATE employee SET created_at = TIMESTAMPTZ '2030-03-28 00:00+09' WHERE id = ?", kim.id());
        jdbc.update("UPDATE employee SET hire_date = DATE '2030-03-11', created_at = TIMESTAMPTZ '2030-04-01 00:00+09' WHERE id = ?", lee.id());
        jdbc.update("UPDATE employee SET status = 'RESIGNED', created_at = TIMESTAMPTZ '2030-04-01 00:00+09' WHERE id = ?", park.id());
        jdbc.update("""
                INSERT INTO employment_status_history (company_id, employee_id, status, effective_date, reason)
                VALUES (?, ?, 'RESIGNED', DATE '2030-03-21', '퇴사')""", cid, park.id());
        jdbc.update("UPDATE employee SET bank_name = '신한', bank_account_enc = 'x', bank_account_last4 = '1234' WHERE id = ?", kim.id());

        salary(kim.id(), 3_000_000, "2024-03-01");
        salary(lee.id(), 3_100_000, "2030-03-11");
        salary(park.id(), 3_100_000, "2024-03-01");

        item("{\"name\": \"식대\", \"itemKind\": \"EARNING\", \"calcMethod\": \"FIXED\", \"defaultAmount\": 200000,"
                + " \"isTaxable\": false, \"nonTaxableLimit\": 200000, \"inOrdinaryWage\": true, \"sortOrder\": 10}");
        bonus = item("{\"name\": \"성과급\", \"itemKind\": \"EARNING\", \"calcMethod\": \"MANUAL\", \"sortOrder\": 20}");

        // 김: 3/4 09:00–21:00 근무 + 연장 120분 승인
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                                        check_in_method, check_out_method)
                VALUES (?, ?, DATE '2030-03-04', 'CHECKED_OUT', 'OFFICE', TIMESTAMPTZ '2030-03-04 09:00+09',
                        TIMESTAMPTZ '2030-03-04 21:00+09', 'WEB', 'WEB')""", cid, kim.id());
        jdbc.update("""
                INSERT INTO overtime_request (company_id, employee_id, work_date, planned_start, planned_end,
                                              requested_minutes, approved_minutes, reason, status)
                VALUES (?, ?, DATE '2030-03-04', TIMESTAMPTZ '2030-03-04 18:00+09', TIMESTAMPTZ '2030-03-04 21:00+09',
                        180, 120, '배포', 'APPROVED')""", cid, kim.id());
        // 김: 승인된 출장 경비 59,900 (정산 대기)
        long trip = jdbc.queryForObject("""
                INSERT INTO business_trip (company_id, employee_id, trip_type, destination, purpose, start_date, end_date, status)
                VALUES (?, ?, 'DOMESTIC', '부산', '방문', DATE '2030-03-05', DATE '2030-03-05', 'APPROVED') RETURNING id""",
                Long.class, cid, kim.id());
        long claim = jdbc.queryForObject("""
                INSERT INTO expense_claim (company_id, business_trip_id, employee_id, status) VALUES (?, ?, ?, 'APPROVED')
                RETURNING id""", Long.class, cid, trip, kim.id());
        jdbc.update("""
                INSERT INTO expense_claim_line (company_id, expense_claim_id, expense_type_id, used_date, amount)
                VALUES (?, ?, (SELECT id FROM expense_type WHERE company_id = ? AND name = '교통비'), DATE '2030-03-05', 59900)""",
                cid, claim, cid);
        // 김: 배우자 · 자녀 2명 공제 대상, 부모는 대상 아님
        jdbc.update("""
                INSERT INTO employee_family (company_id, employee_id, name, relation, is_tax_dependent) VALUES
                  (?, ?, '배우자', 'SPOUSE', TRUE), (?, ?, '첫째', 'CHILD', TRUE), (?, ?, '둘째', 'CHILD', TRUE),
                  (?, ?, '어머니', 'PARENT', FALSE)""", cid, kim.id(), cid, kim.id(), cid, kim.id(), cid, kim.id());
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return as(company.adminId(), request);
    }

    private ResultActions run(String path, String body) throws Exception {
        return admin(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String manual(long employeeId, long amount) {
        return ", \"manualInputs\": [{\"employeeId\": %d, \"payItemId\": %d, \"amount\": %d}]"
                .formatted(employeeId, bonus, amount);
    }

    private void salary(long employeeId, long monthly, String from) {
        jdbc.update("""
                INSERT INTO employee_salary (company_id, employee_id, salary_type, monthly_base, effective_from, reason, created_by)
                VALUES (?, ?, 'MONTHLY', ?, ?::date, '등록', ?)""", company.id(), employeeId, monthly, from, company.adminId());
    }

    private long item(String body) throws Exception {
        String res = admin(post("/api/pay-items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private static String emp(TestFixture.Employee e) {
        return "$.data.employees[?(@.employeeId == %d)]".formatted(e.id());
    }

    @Test
    void 미리보기는_월이_끝나기_전에도_되고_경고와_다음_달_지급일을_준다() throws Exception {
        clock.set(LocalDateTime.of(2030, 3, 25, 10, 0));
        run("/api/payroll-runs/preview", MARCH.formatted(manual(kim.id(), 500_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payMonth").value("2030-03"))
                .andExpect(jsonPath("$.data.payDate").value("2030-04-25"))
                .andExpect(jsonPath("$.data.warning").value("PAY_MONTH_NOT_ENDED"))
                .andExpect(jsonPath("$.data.totals.headcount").value(3))
                .andExpect(jsonPath("$.data.unregistered[*].employeeId")
                        .value(containsInAnyOrder((int) company.adminId(), (int) choi.id())))
                .andExpect(jsonPath("$.data.unregistered[0].reason").value("PAY_SALARY_MISSING"))
                .andExpect(jsonPath(emp(kim) + ".attendance.absentDays").value(0));       // 3/28 은 아직 오지 않았다
        run("/api/payroll-runs", MARCH.formatted(""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 월이_끝난_뒤_미리보기는_근태_경비_가족_일할을_반영한다() throws Exception {
        clock.set(LocalDateTime.of(2030, 4, 2, 10, 0));
        run("/api/payroll-runs/preview", MARCH.formatted(manual(kim.id(), 500_000)))
                .andExpect(jsonPath("$.data.warning").value(nullValue()))
                // 김
                .andExpect(jsonPath(emp(kim) + ".ordinaryHourlyWage").value(15311))
                .andExpect(jsonPath(emp(kim) + ".attendance.overtimeMinutes").value(120))
                .andExpect(jsonPath(emp(kim) + ".attendance.absentDays").value(2))
                .andExpect(jsonPath(emp(kim) + ".attendance.absenceMinutes").value(960))
                .andExpect(jsonPath(emp(kim) + ".lines[?(@.name == '연장근로수당')].amount").value(45933))
                .andExpect(jsonPath(emp(kim) + ".lines[?(@.name == '연장근로수당')].formulaNote").value("2시간 × 15,311원 × 1.5"))
                .andExpect(jsonPath(emp(kim) + ".lines[?(@.name == '결근 공제')].amount").value(244976))
                .andExpect(jsonPath(emp(kim) + ".lines[?(@.name == '결근 공제')].formulaNote").value("2일(16시간) × 15,311원 × 1"))
                .andExpect(jsonPath(emp(kim) + ".lines[?(@.name == '출장비 정산')].nonTaxableAmount").value(59900))
                .andExpect(jsonPath(emp(kim) + ".lines[?(@.name == '성과급')].amount").value(500000))
                .andExpect(jsonPath(emp(kim) + ".lines[?(@.name == '국민연금')].companyAmount").exists())
                .andExpect(jsonPath(emp(kim) + ".grossPay").value(3805833))                  // 300만 + 20만 + 45,933 + 50만 + 59,900
                .andExpect(jsonPath(emp(kim) + ".taxablePay").value(3545933))                // − 비과세 20만 · 59,900
                .andExpect(jsonPath(emp(kim) + ".dependentsCount").value(3))
                .andExpect(jsonPath(emp(kim) + ".childrenCount").value(2))
                .andExpect(jsonPath(emp(kim) + ".expenseClaimIds.length()").value(1))
                // 이(3/11 입사) · 박(3/21 퇴직 발효)
                .andExpect(jsonPath(emp(lee) + ".workedDays").value(21))
                .andExpect(jsonPath(emp(lee) + ".basePay").value(2100000))                   // ⌊310만 × 21 ÷ 31⌋
                .andExpect(jsonPath(emp(lee) + ".lines[?(@.name == '식대')].amount").value(135483))
                .andExpect(jsonPath(emp(park) + ".workedDays").value(20))
                .andExpect(jsonPath(emp(park) + ".basePay").value(2000000));
    }

    @Test
    void 수동_입력은_활성_수동_항목과_정산_대상만_한_번씩() throws Exception {
        long meal = jdbc.queryForObject("SELECT id FROM pay_item WHERE company_id = ? AND name = '식대'", Long.class, company.id());
        run("/api/payroll-runs/preview", """
                {"payMonth": "2030-03", "manualInputs": [
                  {"employeeId": %d, "payItemId": %d, "amount": 1},
                  {"employeeId": %d, "payItemId": %d, "amount": 1},
                  {"employeeId": %d, "payItemId": %d, "amount": 1},
                  {"employeeId": %d, "payItemId": %d, "amount": 2}]}
                """.formatted(kim.id(), meal, choi.id(), bonus, kim.id(), bonus, kim.id(), bonus))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['manualInputs[0].payItemId']").exists())
                .andExpect(jsonPath("$.error.fields['manualInputs[1].employeeId']").exists())
                .andExpect(jsonPath("$.error.fields['manualInputs[3]']").exists());
    }

    @Test
    void 확정하면_명세서와_줄을_저장하고_경비를_연결하고_같은_달은_다시_못_한다() throws Exception {
        clock.set(LocalDateTime.of(2030, 4, 2, 10, 0));
        String body = MARCH.formatted(", \"payDate\": \"2030-04-10\"" + manual(kim.id(), 500_000));
        String preview = run("/api/payroll-runs/preview", body).andReturn().getResponse().getContentAsString();
        String res = run("/api/payroll-runs", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.payDate").value("2030-04-10"))
                .andExpect(jsonPath("$.data.totals.headcount").value(3))
                .andExpect(jsonPath("$.data.paystubs.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        // 미리보기와 같은 결과를 저장했다
        assertThat(((Number) JsonPath.read(res, "$.data.totals.netPay")).longValue())
                .isEqualTo(((Number) JsonPath.read(preview, "$.data.totals.netPay")).longValue());
        long runId = ((Number) JsonPath.read(res, "$.data.id")).longValue();

        long kimStub = jdbc.queryForObject("SELECT id FROM paystub WHERE payroll_run_id = ? AND employee_id = ?",
                Long.class, runId, kim.id());
        assertThat(jdbc.queryForObject("SELECT paystub_id FROM expense_claim WHERE employee_id = ?", Long.class, kim.id()))
                .isEqualTo(kimStub);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'PAYROLL_RUN' AND action = 'EXECUTE'",
                Long.class, company.id())).isEqualTo(1);

        run("/api/payroll-runs", body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAY_MONTH_SETTLED"));
        run("/api/payroll-runs/preview", body).andExpect(jsonPath("$.error.code").value("PAY_MONTH_SETTLED"));
        // 정산된 월은 연장근무 신청도 막힌다(B-12)
        as(kim.id(), post("/api/me/overtime-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\": \"2030-03-29\", \"plannedStart\": \"18:00\", \"plannedEnd\": \"20:00\", \"reason\": \"x\"}"))
                .andExpect(jsonPath("$.error.code").value("PAY_MONTH_SETTLED"));

        admin(get("/api/payroll-runs"))
                .andExpect(jsonPath("$.data[0].payMonth").value("2030-03"))
                .andExpect(jsonPath("$.data[0].confirmedAt").exists())                    // 상세에서 paystubs 를 뺀 모양
                .andExpect(jsonPath("$.data[0].confirmedByName").exists())
                .andExpect(jsonPath("$.data[0].paidAt").value(nullValue()))
                .andExpect(jsonPath("$.data[0].paystubs").doesNotExist())
                .andExpect(jsonPath("$.data[0].totals.headcount").value(3));
        admin(post("/api/payroll-runs/" + runId + "/paid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt").value("2030-04-02T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.paidByName").exists());
        admin(post("/api/payroll-runs/" + runId + "/paid")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 명세서는_본인과_PAYROLL_READ_만_보고_본인에게는_회사부담이_없다() throws Exception {
        clock.set(LocalDateTime.of(2030, 4, 2, 10, 0));
        run("/api/payroll-runs", MARCH.formatted(manual(kim.id(), 500_000))).andExpect(status().isCreated());
        long kimStub = jdbc.queryForObject("SELECT id FROM paystub WHERE employee_id = ?", Long.class, kim.id());
        long leeStub = jdbc.queryForObject("SELECT id FROM paystub WHERE employee_id = ?", Long.class, lee.id());

        as(kim.id(), get("/api/me/payslips"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].payMonth").value("2030-03"))
                .andExpect(jsonPath("$.data[0].payDate").value("2030-04-25"));
        as(kim.id(), get("/api/me/payslips/" + kimStub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.name").exists())
                .andExpect(jsonPath("$.data.bankAccount").value("신한 ***-****-1234"))
                .andExpect(jsonPath("$.data.companyBurdenTotal").doesNotExist())
                .andExpect(jsonPath("$.data.lines[?(@.name == '국민연금')].companyAmount").doesNotExist())
                .andExpect(jsonPath("$.data.expenseClaimIds.length()").value(1));
        as(kim.id(), get("/api/me/payslips/" + leeStub)).andExpect(status().isNotFound());
        as(kim.id(), get("/api/payslips/" + kimStub)).andExpect(status().isForbidden());

        admin(get("/api/payslips/" + kimStub))
                .andExpect(jsonPath("$.data.companyBurdenTotal").isNumber())
                .andExpect(jsonPath("$.data.lines[?(@.name == '국민연금')].companyAmount").isNotEmpty());
        admin(get("/api/payslips?payMonth=2030-03")).andExpect(jsonPath("$.data.totalElements").value(3));
        admin(get("/api/payslips?orgUnitId=" + team)).andExpect(jsonPath("$.data.totalElements").value(2));

        assertThat(payslipService.latest(company.id(), kim.id())).get()
                .extracting(p -> p.payMonth()).isEqualTo("2030-03");
        assertThat(payslipService.latest(company.id(), choi.id())).isEmpty();
    }

    @Test
    void 조직별_인건비는_예산_있는_조직마다_하위_포함_정산_당시_소속으로() throws Exception {
        clock.set(LocalDateTime.of(2030, 4, 2, 10, 0));
        jdbc.update("UPDATE org_unit SET monthly_budget = 20000000 WHERE id = ?", company.rootOrgUnitId());
        jdbc.update("UPDATE org_unit SET monthly_budget = 1000000 WHERE id = ?", team);
        admin(get("/api/statistics/labor-cost?payMonth=2030-03"))
                .andExpect(jsonPath("$.data.settled").value(false))
                .andExpect(jsonPath("$.data.rows[0].laborCost").value(0));
        run("/api/payroll-runs", MARCH.formatted("")).andExpect(status().isCreated());

        long all = jdbc.queryForObject("SELECT sum(gross_pay + company_burden_total) FROM paystub WHERE company_id = ?",
                Long.class, company.id());
        long teamCost = jdbc.queryForObject("""
                SELECT sum(gross_pay + company_burden_total) FROM paystub WHERE company_id = ? AND org_unit_id_snap = ?""",
                Long.class, company.id(), team);
        admin(get("/api/statistics/labor-cost?payMonth=2030-03"))
                .andExpect(jsonPath("$.data.settled").value(true))
                .andExpect(jsonPath("$.data.rows[?(@.orgUnitId == %d)].laborCost".formatted(company.rootOrgUnitId())).value((int) all))
                .andExpect(jsonPath("$.data.rows[?(@.orgUnitId == %d)].laborCost".formatted(team)).value((int) teamCost));
    }

    @Test
    void 정산은_PAYROLL_MANAGE_조회는_PAYROLL_READ() throws Exception {
        TestFixture.Employee executive = fixture.employee(company.id(), company.rootOrgUnitId(), "경영진", false);
        as(kim.id(), post("/api/payroll-runs/preview").contentType(MediaType.APPLICATION_JSON).content(MARCH.formatted("")))
                .andExpect(status().isForbidden());
        as(executive.id(), post("/api/payroll-runs/preview").contentType(MediaType.APPLICATION_JSON).content(MARCH.formatted("")))
                .andExpect(status().isForbidden());
        as(executive.id(), get("/api/payroll-runs")).andExpect(status().isOk());
        as(kim.id(), get("/api/statistics/labor-cost?payMonth=2030-03")).andExpect(status().isForbidden());
    }
}
