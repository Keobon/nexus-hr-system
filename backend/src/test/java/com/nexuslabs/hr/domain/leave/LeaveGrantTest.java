package com.nexuslabs.hr.domain.leave;

import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.domain.leave.dto.LeaveGrantItem;
import com.nexuslabs.hr.domain.leave.service.LeaveGrantService;
import com.nexuslabs.hr.domain.leave.service.LeaveGrantType;
import com.nexuslabs.hr.domain.leave.service.LeaveYear;
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

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-LEAVE-02 휴가 부여 — 연도 일괄 · 입사 · 개별 조정 (API 설계서 8장, BR-LEAVE-006·007). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveGrantTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired LeaveGrantService leaveGrantService;

    TestFixture.Company company;
    long annualType;

    @BeforeEach
    void setUp() {
        company = fixture.company("휴가부여테스트");
        annualType = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, company.id());
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private TestFixture.Employee employee(LocalDate hireDate) {
        TestFixture.Employee e = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        jdbc.update("UPDATE employee SET hire_date = ? WHERE id = ?", Date.valueOf(hireDate), e.id());
        return e;
    }

    private void statusHistory(long employeeId, String status, LocalDate effectiveDate) {
        jdbc.update("""
                INSERT INTO employment_status_history (company_id, employee_id, status, effective_date, reason)
                VALUES (?, ?, ?::emp_status, ?, '테스트')""", company.id(), employeeId, status, Date.valueOf(effectiveDate));
    }

    private Integer grantedDays(long employeeId, long leaveTypeId, int year) {
        return jdbc.queryForObject("""
                SELECT sum(days) FROM leave_grant WHERE employee_id = ? AND leave_type_id = ? AND leave_year = ?""",
                Integer.class, employeeId, leaveTypeId, year);
    }

    @Test
    void 연도_일괄_부여는_연도_시작일_재직중_휴직자에게_근속_가산_포함() throws Exception {
        TestFixture.Employee junior = employee(LocalDate.of(2024, 3, 1));
        TestFixture.Employee senior = employee(LocalDate.of(2021, 1, 1));
        TestFixture.Employee onLeave = employee(LocalDate.of(2024, 3, 1));
        statusHistory(onLeave.id(), "ON_LEAVE", LocalDate.of(2025, 11, 1));
        TestFixture.Employee resigned = employee(LocalDate.of(2024, 3, 1));
        statusHistory(resigned.id(), "RESIGNED", LocalDate.of(2025, 12, 31));
        employee(LocalDate.of(2026, 2, 1)); // 연도 시작 뒤 입사 → 입사 부여 대상이라 제외
        // 회사 등록 관리자는 오늘 입사라 제외. 대상 3명 × 연간 부여일수 > 0 인 종류 1개(연차)

        asAdmin(json(post("/api/leave-grants/annual"), "{\"leaveYear\": 2026}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(3))
                .andExpect(jsonPath("$.data.skipped").value(0));
        assertThat(grantedDays(junior.id(), annualType, 2026)).isEqualTo(15);
        assertThat(grantedDays(senior.id(), annualType, 2026)).isEqualTo(17);
        assertThat(grantedDays(onLeave.id(), annualType, 2026)).isEqualTo(15);
        assertThat(grantedDays(resigned.id(), annualType, 2026)).isNull();

        // 두 번 눌러도 중복되지 않는다(BR-LEAVE-007)
        asAdmin(json(post("/api/leave-grants/annual"), "{\"leaveYear\": 2026}"))
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.skipped").value(3));
        assertThat(grantedDays(junior.id(), annualType, 2026)).isEqualTo(15);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'LEAVE_GRANT' AND action = 'EXECUTE'""",
                Integer.class, company.id())).isEqualTo(2);
    }

    @Test
    void 입사_부여_뒤_일괄_부여는_건너뛴다() throws Exception {
        LeaveYear current = LeaveYear.containing(LocalDate.now(ClockConfig.ZONE), 1);
        // 연도 시작 전 입사자를 나중에 등록 → 정기 부여와 같은 일수를 입사 부여로
        TestFixture.Employee lateRegistered = employee(current.start().minusYears(1));
        List<LeaveGrantItem> grants = leaveGrantService.grantOnHire(company.id(), lateRegistered.id());
        assertThat(grants).singleElement().satisfies(g -> {
            assertThat(g.leaveTypeName()).isEqualTo("연차");
            assertThat(g.grantType()).isEqualTo(LeaveGrantType.HIRE);
            assertThat(g.leaveYear()).isEqualTo(current.year());
            assertThat(g.days()).isEqualTo(15);
        });

        asAdmin(json(post("/api/leave-grants/annual"), "{\"leaveYear\": " + current.year() + "}"))
                .andExpect(jsonPath("$.data.skipped").value(1));
        assertThat(grantedDays(lateRegistered.id(), annualType, current.year())).isEqualTo(15);

        // 연도 중 입사 → 첫해 비례(3번째 달 입사 = 남은 10개월 → 12일)
        TestFixture.Employee newcomer = employee(current.start().plusMonths(2));
        assertThat(leaveGrantService.grantOnHire(company.id(), newcomer.id()))
                .singleElement().extracting(LeaveGrantItem::days).isEqualTo(12);
        // 같은 직원에게 다시 불러도 늘지 않는다
        assertThat(leaveGrantService.grantOnHire(company.id(), newcomer.id())).isEmpty();
    }

    @Test
    void 조정은_사유_필수이고_잔여가_음수가_되면_거부() throws Exception {
        TestFixture.Employee e = employee(LocalDate.of(2024, 3, 1));
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2026, 'REGULAR', 15)""", company.id(), e.id(), annualType);
        jdbc.update("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days, status)
                VALUES (?, ?, ?, 2026, '2026-05-04', '2026-05-08', 5, 'APPROVED')""", company.id(), e.id(), annualType);

        String body = "{\"employeeId\": %d, \"leaveTypeId\": %d, \"leaveYear\": 2026, \"days\": %d, \"reason\": %s}";
        asAdmin(json(post("/api/leave-grants/adjustments"), body.formatted(e.id(), annualType, 1, "\"포상\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.grantType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.data.days").value(1))
                .andExpect(jsonPath("$.data.createdById").value(company.adminId()));

        // 잔여 = 16 − 5 = 11 → −12 는 거부, −11 은 허용
        asAdmin(json(post("/api/leave-grants/adjustments"), body.formatted(e.id(), annualType, -12, "\"정정\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAVE_INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.error.details.remaining").value(11))
                .andExpect(jsonPath("$.error.details.requested").value(-12));
        asAdmin(json(post("/api/leave-grants/adjustments"), body.formatted(e.id(), annualType, -11, "\"정정\"")))
                .andExpect(status().isCreated());

        asAdmin(json(post("/api/leave-grants/adjustments"), body.formatted(e.id(), annualType, 1, "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").value("필수 항목입니다"));
        asAdmin(json(post("/api/leave-grants/adjustments"), body.formatted(e.id(), annualType, 0, "\"영\"")))
                .andExpect(status().isBadRequest());

        asAdmin(get("/api/leave-grants").param("employeeId", String.valueOf(e.id())).param("leaveYear", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].grantType").value("REGULAR"))
                .andExpect(jsonPath("$.data[0].createdById").doesNotExist())
                .andExpect(jsonPath("$.data[1].reason").value("포상"));
    }

    @Test
    void 부여는_LEAVE_MANAGE만_다른_회사_직원은_404() throws Exception {
        TestFixture.Employee staff = employee(LocalDate.of(2024, 3, 1));
        mvc.perform(json(post("/api/leave-grants/annual"), "{\"leaveYear\": 2026}")
                        .header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isForbidden());

        TestFixture.Company other = fixture.company("다른회사");
        asAdmin(get("/api/leave-grants").param("employeeId", String.valueOf(other.adminId())))
                .andExpect(status().isNotFound());
        asAdmin(json(post("/api/leave-grants/adjustments"), """
                {"employeeId": %d, "leaveTypeId": %d, "leaveYear": 2026, "days": 1, "reason": "x"}
                """.formatted(other.adminId(), annualType)))
                .andExpect(status().isNotFound());
    }
}
