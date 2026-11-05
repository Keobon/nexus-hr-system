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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 급여 확정 취소(2026-11-05). 지급완료 전(확정)만 사유와 함께 취소하고, 그 달을 다시 정산한다.
 * 취소된 정산 · 명세서는 남기되 직원 · 목록 · 통계 · "정산된 달" 판단에서 뺀다. 시계는 2030-04-02(3월 정산 가능).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class PayrollCancelTest {

    private static final String MARCH = "{\"payMonth\": \"2030-03\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    TestFixture.Employee kim;
    long claim;

    @BeforeEach
    void setUp() {
        clock.set(LocalDateTime.of(2030, 4, 2, 10, 0));
        company = fixture.company("급여취소테스트");
        long cid = company.id();
        kim = fixture.employee(cid, company.rootOrgUnitId(), "직원", false);
        // 결근 공제가 붙지 않게 등록일을 3월 뒤로
        jdbc.update("UPDATE employee SET created_at = TIMESTAMPTZ '2030-04-01 00:00+09' WHERE id = ?", kim.id());
        jdbc.update("""
                INSERT INTO employee_salary (company_id, employee_id, salary_type, monthly_base, effective_from, reason, created_by)
                VALUES (?, ?, 'MONTHLY', 3000000, DATE '2024-03-01', '등록', ?)""", cid, kim.id(), company.adminId());
        long trip = jdbc.queryForObject("""
                INSERT INTO business_trip (company_id, employee_id, trip_type, destination, purpose, start_date, end_date, status)
                VALUES (?, ?, 'DOMESTIC', '부산', '방문', DATE '2030-03-05', DATE '2030-03-05', 'APPROVED') RETURNING id""",
                Long.class, cid, kim.id());
        claim = jdbc.queryForObject("""
                INSERT INTO expense_claim (company_id, business_trip_id, employee_id, status) VALUES (?, ?, ?, 'APPROVED')
                RETURNING id""", Long.class, cid, trip, kim.id());
        jdbc.update("""
                INSERT INTO expense_claim_line (company_id, expense_claim_id, expense_type_id, used_date, amount)
                VALUES (?, ?, (SELECT id FROM expense_type WHERE company_id = ? AND name = '교통비'), DATE '2030-03-05', 59900)""",
                cid, claim, cid);
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

    private long confirm() throws Exception {
        String res = admin(json(post("/api/payroll-runs"), MARCH)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private ResultActions cancel(long runId, String reason) throws Exception {
        return admin(json(post("/api/payroll-runs/" + runId + "/cancel"), "{\"reason\": \"%s\"}".formatted(reason)));
    }

    private long stubOf(long runId) {
        return jdbc.queryForObject("SELECT id FROM paystub WHERE payroll_run_id = ? AND employee_id = ?", Long.class,
                runId, kim.id());
    }

    @Test
    void 확정을_취소하면_그_달을_다시_정산하고_경비는_새_명세서에_붙는다() throws Exception {
        long first = confirm();
        assertThat(jdbc.queryForObject("SELECT paystub_id FROM expense_claim WHERE id = ?", Long.class, claim))
                .isEqualTo(stubOf(first));

        cancel(first, "성과급 누락").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("성과급 누락"))
                .andExpect(jsonPath("$.data.cancelledAt").value("2030-04-02T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.cancelledByName").exists());
        assertThat(jdbc.queryForObject("SELECT paystub_id FROM expense_claim WHERE id = ?", Long.class, claim)).isNull();
        // 명세서는 지우지 않는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paystub WHERE payroll_run_id = ?", Long.class, first)).isOne();

        admin(json(post("/api/payroll-runs/preview"), MARCH)).andExpect(status().isOk());
        long second = confirm();
        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT paystub_id FROM expense_claim WHERE id = ?", Long.class, claim))
                .isEqualTo(stubOf(second));

        admin(get("/api/payroll-runs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(second));
        admin(get("/api/payroll-runs?includeCancelled=true"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == %d)].status".formatted(first)).value("CANCELLED"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'PAYROLL_RUN' AND action = 'UPDATE'""",
                Long.class, company.id())).isOne();
    }

    @Test
    void 지급완료나_이미_취소된_정산은_취소할_수_없고_사유가_필요하다() throws Exception {
        long run = confirm();
        cancel(run, "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        cancel(run, "다시").andExpect(status().isOk());
        cancel(run, "또").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

        long paid = confirm();
        admin(post("/api/payroll-runs/" + paid + "/paid")).andExpect(status().isOk());
        cancel(paid, "늦음").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        // 취소된 정산은 지급완료로 바꿀 수도 없다
        admin(post("/api/payroll-runs/" + run + "/paid")).andExpect(status().isConflict());
    }

    @Test
    void 권한이_없거나_다른_회사_정산이면_거부한다() throws Exception {
        long run = confirm();
        as(kim.id(), json(post("/api/payroll-runs/" + run + "/cancel"), "{\"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
        TestFixture.Company other = fixture.company("급여취소다른회사");
        mvc.perform(json(post("/api/payroll-runs/" + run + "/cancel"), "{\"reason\": \"x\"}")
                        .header("Authorization", fixture.token(other.adminId(), other.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void 취소된_달은_명세서_통계_정산된_달_판단에서_빠진다() throws Exception {
        long run = confirm();
        long stub = stubOf(run);
        cancel(run, "재정산").andExpect(status().isOk());

        as(kim.id(), get("/api/me/payslips")).andExpect(jsonPath("$.data.length()").value(0));
        as(kim.id(), get("/api/me/payslips/" + stub)).andExpect(status().isNotFound());
        as(kim.id(), get("/api/dashboard/home")).andExpect(jsonPath("$.data.me.latestPayslip").value(nullValue()));
        admin(get("/api/payslips?payMonth=2030-03")).andExpect(jsonPath("$.data.totalElements").value(0));
        admin(get("/api/statistics/labor-cost?payMonth=2030-03")).andExpect(jsonPath("$.data.settled").value(false));
        // 정산 전 달처럼 연장근무 신청 · 근태 정정이 된다(연장근무는 승인자가 없어 다른 오류지만 "정산된 월"은 아니다)
        as(kim.id(), json(post("/api/me/overtime-requests"),
                "{\"workDate\": \"2030-03-29\", \"plannedStart\": \"18:00\", \"plannedEnd\": \"20:00\", \"reason\": \"x\"}"))
                .andExpect(jsonPath("$.error.code").value(not("PAY_MONTH_SETTLED")));
        long attendance = jdbc.queryForObject("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_in_method)
                VALUES (?, ?, DATE '2030-03-28', 'CHECKED_IN', 'OFFICE', TIMESTAMPTZ '2030-03-28 09:00+09', 'WEB') RETURNING id""",
                Long.class, company.id(), kim.id());
        admin(json(patch("/api/attendances/" + attendance),
                "{\"status\": \"CHECKED_OUT\", \"checkOutAt\": \"2030-03-28T18:00:00+09:00\", \"reason\": \"퇴근 누락\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warning").value(nullValue()));
    }
}
