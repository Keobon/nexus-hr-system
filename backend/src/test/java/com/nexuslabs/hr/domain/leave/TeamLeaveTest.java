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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/me/team-leaves — 팀 휴가 캘린더(역할 분담 v2 2.3 L 91). 권한 없이 로그인한 직원 누구나.
 * 같은 조직(하위 제외) 재직자의 진행 중 휴가를 이름 · 종류 · 기간 · 상태만.
 * 조직: 개발본부(강하늘) ┬ 프론트엔드팀(서예린 · 조현우) / └ 백엔드팀(윤서연).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeamLeaveTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    DemoOrg org;
    long cid;
    long annual;

    @BeforeEach
    void setUp() {
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'", Long.class, cid);
    }

    private void leave(TestFixture.Employee e, String start, String end, String status) {
        jdbc.update("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days,
                                           reason, status)
                VALUES (?, ?, ?, 2030, ?::date, ?::date, 1, '개인 사유', ?::leave_status)""",
                cid, e.id(), annual, start, end, status);
    }

    private ResultActions teamLeaves(TestFixture.Employee e, String from, String to) throws Exception {
        return mvc.perform(get("/api/me/team-leaves").param("from", from).param("to", to)
                .header("Authorization", fixture.token(e.id(), cid)));
    }

    @Test
    void 같은_조직의_진행_중_휴가만_기간이_걸치면_시작일_순으로() throws Exception {
        leave(org.seo, "2030-03-10", "2030-03-12", "APPROVED");
        leave(org.cho, "2030-03-04", "2030-03-05", "PENDING");
        leave(org.cho, "2030-03-20", "2030-03-20", "CANCEL_REQUESTED");
        leave(org.seo, "2030-03-25", "2030-03-25", "REJECTED");      // 반려 — 빠짐
        leave(org.seo, "2030-03-26", "2030-03-26", "CANCELLED");     // 취소 — 빠짐
        leave(org.seo, "2030-02-25", "2030-03-01", "APPROVED");      // 기간 밖 — 빠짐
        leave(org.cho, "2030-03-31", "2030-04-02", "APPROVED");      // 끝에 걸침
        leave(org.yoon, "2030-03-10", "2030-03-10", "APPROVED");     // 다른 팀 — 빠짐
        leave(org.kang, "2030-03-10", "2030-03-10", "APPROVED");     // 상위 조직 — 빠짐

        teamLeaves(org.cho, "2030-03-02", "2030-03-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[*].startDate",
                        contains("2030-03-04", "2030-03-10", "2030-03-20", "2030-03-31")))
                .andExpect(jsonPath("$.data[*].status",
                        contains("PENDING", "APPROVED", "CANCEL_REQUESTED", "APPROVED")))
                .andExpect(jsonPath("$.data[1].employeeId").value(org.seo.id()))
                .andExpect(jsonPath("$.data[1].employeeName").isString())
                .andExpect(jsonPath("$.data[1].leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data[1].endDate").value("2030-03-12"))
                .andExpect(jsonPath("$.data[1].reason").doesNotExist());
    }

    @Test
    void 퇴직한_팀원의_휴가는_빠진다() throws Exception {
        leave(org.seo, "2030-03-10", "2030-03-10", "APPROVED");
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", org.seo.id());

        teamLeaves(org.cho, "2030-03-01", "2030-03-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()));
    }

    @Test
    void 시작일이_종료일보다_뒤면_VALIDATION_ERROR() throws Exception {
        teamLeaves(org.cho, "2030-03-31", "2030-03-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
