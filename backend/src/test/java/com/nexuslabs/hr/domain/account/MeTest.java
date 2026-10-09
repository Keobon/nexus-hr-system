package com.nexuslabs.hr.domain.account;

import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/me — 메뉴·화면 부트스트랩. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("내정보테스트");
    }

    private ResultActions me(long employeeId) throws Exception {
        return mvc.perform(get("/api/me").header("Authorization", fixture.token(employeeId, company.id())));
    }

    @Test
    void 직원_역할의_조직장은_팀_권한과_조직장_정보를_받는다() throws Exception {
        long team = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "개발팀");
        TestFixture.Employee lead = fixture.employee(company.id(), team, "직원", false);
        fixture.lead(company.id(), team, lead.id());

        me(lead.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employee.orgUnitName").value("개발팀"))
                .andExpect(jsonPath("$.data.employee.employeeNo").value("2024-0001"))
                .andExpect(jsonPath("$.data.employee.payrollEligible").value(true))
                .andExpect(jsonPath("$.data.company.name").value("내정보테스트"))
                .andExpect(jsonPath("$.data.role.name").value("직원"))
                .andExpect(jsonPath("$.data.permissions.length()").value(5))
                .andExpect(jsonPath("$.data.permissions[0].code").value("EMPLOYEE_READ"))
                .andExpect(jsonPath("$.data.permissions[0].scope").value("TEAM"))
                .andExpect(jsonPath("$.data.isOrgLead").value(true))
                .andExpect(jsonPath("$.data.leadOrgUnitIds").value(contains((int) team)))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void 값이_없는_필드는_null로_내려간다() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        me(staff.id())
                .andExpect(jsonPath("$.data.employee.jobGradeName").hasJsonPath())
                .andExpect(jsonPath("$.data.employee.jobGradeName").doesNotExist())
                .andExpect(jsonPath("$.data.employee.profileFileId").hasJsonPath())
                .andExpect(jsonPath("$.data.company.logoFileId").hasJsonPath())
                .andExpect(jsonPath("$.data.isOrgLead").value(false))
                .andExpect(jsonPath("$.data.leadOrgUnitIds").value(empty()))
                // 권한이 없는 배지는 null
                .andExpect(jsonPath("$.data.todos.approvalsPending").value(0))
                .andExpect(jsonPath("$.data.todos.reassignNeeded").hasJsonPath())
                .andExpect(jsonPath("$.data.todos.reassignNeeded").doesNotExist())
                .andExpect(jsonPath("$.data.todos.attendanceCorrections").doesNotExist());
    }

    @Test
    void 할_일_건수() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        // 관리자에게 내 차례 1건, 재지정 필요 1건(승인자 퇴직). 유일 제약에 회사가 없어 실제 신청 id 와 겹치지 않는 큰 값을 쓴다
        long target = 900_000_000L + company.id() * 2;
        jdbc.update("""
                INSERT INTO approval_step (company_id, work_type, target_id, step_order, approver_id, status, needs_reassign)
                VALUES (?, 'LEAVE', ?, 1, ?, 'PENDING', FALSE), (?, 'LEAVE', ?, 1, ?, 'PENDING', TRUE)
                """, company.id(), target, company.adminId(), company.id(), target + 1, staff.id());
        // 퇴근미기록 1건
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, check_in_at)
                VALUES (?, ?, DATE '2026-09-30', 'MISSING_CHECKOUT', TIMESTAMPTZ '2026-09-30 09:00+09')
                """, company.id(), staff.id());

        me(company.adminId())
                .andExpect(jsonPath("$.data.todos.approvalsPending").value(1))
                .andExpect(jsonPath("$.data.todos.evaluationsToSubmit").value(0))
                .andExpect(jsonPath("$.data.todos.reassignNeeded").value(1))
                .andExpect(jsonPath("$.data.todos.attendanceCorrections").value(1));
        me(staff.id())
                .andExpect(jsonPath("$.data.todos.approvalsPending").value(1));
    }

    @Test
    void 정정_배지는_정정_목록과_같은_조건으로_센다() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        // 9월에도 월–금 근무가 되도록 지난 근무시간 행을 넣는다(회사 등록 때 행은 등록일부터라서)
        jdbc.update("""
                INSERT INTO work_schedule (company_id, effective_from, start_time, end_time, break_minutes,
                                           late_grace_minutes, night_start, night_end, overtime_approval_required, work_days)
                VALUES (?, DATE '2026-01-01', '09:00', '18:00', 60, 0, '22:00', '06:00', TRUE, 31)""", company.id());
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, company.id());
        // 승인된 휴가 9/4(금)–9/7(월)
        jdbc.update("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days, status)
                VALUES (?, ?, ?, 2026, DATE '2026-09-04', DATE '2026-09-07', 2, 'APPROVED')""",
                company.id(), staff.id(), annual);
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, check_in_at, check_out_at, corrected_at)
                VALUES (?, ?, DATE '2026-09-04', 'CHECKED_OUT', TIMESTAMPTZ '2026-09-04 09:00+09', TIMESTAMPTZ '2026-09-04 18:00+09', NULL),
                       (?, ?, DATE '2026-09-05', 'CHECKED_OUT', TIMESTAMPTZ '2026-09-05 09:00+09', TIMESTAMPTZ '2026-09-05 18:00+09', NULL),
                       (?, ?, DATE '2026-09-07', 'CHECKED_OUT', TIMESTAMPTZ '2026-09-07 09:00+09', TIMESTAMPTZ '2026-09-07 18:00+09',
                        now() + INTERVAL '1 minute'),
                       (?, ?, DATE '2026-09-10', 'CHECKED_IN',  TIMESTAMPTZ '2026-09-10 09:00+09', NULL, NULL)""",
                company.id(), staff.id(), company.id(), staff.id(), company.id(), staff.id(), company.id(), staff.id());
        // 센다: 9/4 휴가 근무일 충돌 · 9/10 지난 날짜인데 아직 출근 상태
        // 안 센다: 9/5 휴가 기간 안 토요일 출근 · 9/7 관리자가 휴가 승인 뒤 "확인"한 충돌
        me(company.adminId())
                .andExpect(jsonPath("$.data.todos.attendanceCorrections").value(2));
        mvc.perform(get("/api/attendances/corrections")
                        .header("Authorization", fixture.token(company.adminId(), company.id())))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 비밀번호_변경_전에도_부를_수_있다() throws Exception {
        TestFixture.Employee temp = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", true);
        me(temp.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }
}
