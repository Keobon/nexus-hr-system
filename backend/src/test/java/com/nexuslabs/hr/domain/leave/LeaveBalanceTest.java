package com.nexuslabs.hr.domain.leave;

import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-LEAVE-05 잔여 조회 — 본인 / LEAVE_READ(팀·전사) (API 설계서 8장, BR-LEAVE-003). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveBalanceTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    DemoOrg org;
    long annualType;

    @BeforeEach
    void setUp() {
        org = new DemoOrg(fixture, jdbc);
        annualType = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, org.company.id());
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), org.company.id())));
    }

    private void grant(long employeeId, long leaveTypeId, int days) {
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2026, 'REGULAR', ?)""", org.company.id(), employeeId, leaveTypeId, days);
    }

    private void request(long employeeId, LocalDate start, int days, String status) {
        jdbc.update("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days, status)
                VALUES (?, ?, ?, 2026, ?, ?, ?, ?::leave_status)""",
                org.company.id(), employeeId, annualType, Date.valueOf(start), Date.valueOf(start.plusDays(days - 1)),
                days, status);
    }

    @Test
    void 잔여는_부여_합계_빼기_사용_빼기_승인대기() throws Exception {
        grant(org.cho.id(), annualType, 15);
        request(org.cho.id(), LocalDate.of(2026, 3, 2), 2, "APPROVED");
        request(org.cho.id(), LocalDate.of(2026, 4, 1), 1, "CANCEL_REQUESTED"); // 취소 승인 전까지는 사용
        request(org.cho.id(), LocalDate.of(2026, 5, 4), 3, "PENDING");
        request(org.cho.id(), LocalDate.of(2026, 6, 1), 4, "REJECTED");
        request(org.cho.id(), LocalDate.of(2026, 7, 1), 5, "CANCELLED");

        as(org.cho, get("/api/me/leave-balances").param("leaveYear", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaveYear").value(2026))
                .andExpect(jsonPath("$.data.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-12-31"))
                // 차감 종류(연차)만. 병가·경조사는 차감하지 않아 잔여가 없다
                .andExpect(jsonPath("$.data.balances.length()").value(1))
                .andExpect(jsonPath("$.data.balances[0].leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.balances[0].granted").value(15))
                .andExpect(jsonPath("$.data.balances[0].used").value(3))
                .andExpect(jsonPath("$.data.balances[0].pending").value(3))
                .andExpect(jsonPath("$.data.balances[0].remaining").value(9));
    }

    @Test
    void 비활성_종류는_내역이_있을_때만_보인다() throws Exception {
        long unused = jdbc.queryForObject("""
                INSERT INTO leave_type (company_id, name, annual_days, deducts_balance, sort_order, is_active)
                VALUES (?, '폐지휴가A', 3, TRUE, 10, FALSE) RETURNING id""", Long.class, org.company.id());
        long granted = jdbc.queryForObject("""
                INSERT INTO leave_type (company_id, name, annual_days, deducts_balance, sort_order, is_active)
                VALUES (?, '폐지휴가B', 3, TRUE, 11, FALSE) RETURNING id""", Long.class, org.company.id());
        grant(org.cho.id(), granted, 3);

        as(org.cho, get("/api/me/leave-balances").param("leaveYear", "2026"))
                .andExpect(jsonPath("$.data.balances.length()").value(2))
                .andExpect(jsonPath("$.data.balances[1].leaveTypeName").value("폐지휴가B"));
    }

    @Test
    void 직원별_잔여는_팀_범위면_내_팀만() throws Exception {
        grant(org.cho.id(), annualType, 15);
        grant(org.seo.id(), annualType, 16);

        // 직원 역할 = LEAVE_READ(팀). 서예린은 프론트엔드팀장 → 본인 + 조현우
        as(org.seo, get("/api/leave-balances").param("leaveYear", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[?(@.employeeId == %d)].balances[0].remaining", org.cho.id())
                        .value(15));
        // 조직장이 아니면 팀이 비어 있다
        as(org.cho, get("/api/leave-balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        // 강하늘(개발본부장)은 하위 팀까지
        as(org.kang, get("/api/leave-balances"))
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    void 전사_범위는_조직으로_좁히면_하위_조직_포함_퇴직자_제외() throws Exception {
        // 경영진 = LEAVE_READ(전사). 관리자 + 5명
        as(org.ceo, get("/api/leave-balances"))
                .andExpect(jsonPath("$.data.totalElements").value(6));
        as(org.ceo, get("/api/leave-balances").param("orgUnitId", String.valueOf(org.devDivision)))
                .andExpect(jsonPath("$.data.totalElements").value(4));
        as(org.ceo, get("/api/leave-balances").param("orgUnitId", String.valueOf(org.frontendTeam))
                        .param("size", "1"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", org.yoon.id());
        as(org.ceo, get("/api/leave-balances").param("orgUnitId", String.valueOf(org.devDivision)))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }
}
