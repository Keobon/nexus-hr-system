package com.nexuslabs.hr.domain.dashboard;

import com.nexuslabs.hr.support.DemoOrg;
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

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-DASH-01–05 홈 대시보드 (API 설계서 13장). 섹션은 권한에 따라 붙거나 빠지고, 안 맞으면 필드 자체가 없다.
 * DemoOrg: 최상위(대표 · 관리자) → 개발본부(강하늘) → 프론트엔드팀(서예린 · 조현우) · 백엔드팀(윤). 인사 담당 hr 은 최상위.
 * 시계는 2030-03-04(월) 12:00. 윤은 09:30 재택 출근(지각), 조현우는 3/5–3/6 휴가 승인.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class DashboardTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    TestFixture.Employee hr;
    TestFixture.Employee admin;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.atTime(12, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        hr = fixture.employee(cid, org.company.rootOrgUnitId(), "인사 담당", false);
        admin = new TestFixture.Employee(org.company.adminId(), cid, org.company.adminEmail());
        // 퇴직자 1명(백엔드팀) — 조직 인원에서 빠지고 퇴직 수에 들어간다
        TestFixture.Employee gone = fixture.employee(cid, org.backendTeam, "직원", false);
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", gone.id());

        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_in_method)
                VALUES (?, ?, ?, 'CHECKED_IN', 'REMOTE', TIMESTAMPTZ '2030-03-04 09:30+09', 'WEB')
                """, cid, org.yoon.id(), Date.valueOf(MONDAY));
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'", Long.class, cid);
        long leave = jdbc.queryForObject("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days, status)
                VALUES (?, ?, ?, 2030, '2030-03-05', '2030-03-06', 2, 'APPROVED') RETURNING id""",
                Long.class, cid, org.cho.id(), annual);
        for (String day : new String[]{"2030-03-05", "2030-03-06"}) {
            jdbc.update("""
                    INSERT INTO attendance (company_id, employee_id, work_date, status, check_in_method, leave_request_id)
                    VALUES (?, ?, ?::date, 'ON_VACATION', 'SYSTEM', ?)""", cid, org.cho.id(), day, leave);
        }
        long grade = jdbc.queryForObject("INSERT INTO job_grade (company_id, name, sort_order) VALUES (?, '사원', 1) RETURNING id",
                Long.class, cid);
        as(hr, post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content("""
                {"employeeId": %d, "assignmentType": "TRANSFER", "toOrgUnitId": %d, "toJobGradeId": %d, "reason": "지원"}"""
                .formatted(org.cho.id(), org.frontendTeam, grade))).andExpect(status().isCreated());
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions home(TestFixture.Employee e) throws Exception {
        return as(e, get("/api/dashboard/home")).andExpect(status().isOk());
    }

    @Test
    void 일반_직원은_개인_위젯과_할_일만_있다() throws Exception {
        home(org.cho)
                .andExpect(jsonPath("$.data.me.today.status").value("BEFORE_WORK"))
                .andExpect(jsonPath("$.data.me.today.isWorkday").value(true))
                .andExpect(jsonPath("$.data.me.leaveBalances").isArray())
                .andExpect(jsonPath("$.data.me.latestPayslip").value(nullValue()))       // 급여 대상인데 명세서 없음
                .andExpect(jsonPath("$.data.me.latestEvaluation").value(nullValue()))
                .andExpect(jsonPath("$.data.me.myPending.leave").value(0))
                .andExpect(jsonPath("$.data.me.myPending.expenseClaim").value(0))
                .andExpect(jsonPath("$.data.todos.approvalsPending").value(0))
                .andExpect(jsonPath("$.data.todos.evaluationsToSubmit").value(0))
                .andExpect(jsonPath("$.data.company").doesNotExist())
                .andExpect(jsonPath("$.data.myOrg").doesNotExist())
                .andExpect(jsonPath("$.data.admin").doesNotExist());
        // 확정된 평가가 있으면 최근 1건 — 질문 1개 4점, 배점 100 → 80.0
        long template = jdbc.queryForObject("INSERT INTO eval_template (company_id, name) VALUES (?, '일반') RETURNING id",
                Long.class, cid);
        long criteria = jdbc.queryForObject("""
                INSERT INTO eval_criteria (company_id, eval_template_id, category, name, weight, sort_order)
                VALUES (?, ?, '성과', '성과', 100, 1) RETURNING id""", Long.class, cid, template);
        long question = jdbc.queryForObject("""
                INSERT INTO eval_question (company_id, eval_criteria_id, content, sort_order) VALUES (?, ?, '잘했나', 1)
                RETURNING id""", Long.class, cid, criteria);
        long cycle = jdbc.queryForObject("""
                INSERT INTO eval_cycle (company_id, name, start_date, end_date, status)
                VALUES (?, '상반기', '2030-01-01', '2030-01-31', 'CLOSED') RETURNING id""", Long.class, cid);
        long evaluation = jdbc.queryForObject("""
                INSERT INTO evaluation (company_id, eval_cycle_id, target_employee_id, evaluator_id, eval_template_id, status,
                                        confirmed_at)
                VALUES (?, ?, ?, ?, ?, 'CONFIRMED', TIMESTAMPTZ '2030-02-01 10:00+09') RETURNING id""",
                Long.class, cid, cycle, org.cho.id(), org.seo.id(), template);
        jdbc.update("INSERT INTO eval_answer (company_id, evaluation_id, eval_question_id, score) VALUES (?, ?, ?, 4)",
                cid, evaluation, question);
        home(org.cho)
                .andExpect(jsonPath("$.data.me.latestEvaluation.evaluationId").value(evaluation))
                .andExpect(jsonPath("$.data.me.latestEvaluation.cycleName").value("상반기"))
                .andExpect(jsonPath("$.data.me.latestEvaluation.totalScore").value(80.0));

        // 급여 대상이 아니면 latestPayslip 필드 자체가 없다
        jdbc.update("UPDATE employee SET payroll_eligible = FALSE WHERE id = ?", org.cho.id());
        home(org.cho).andExpect(jsonPath("$.data.me.latestPayslip").doesNotExist());
    }

    @Test
    void 조직장은_내_조직_현황이_붙는다() throws Exception {
        home(org.seo)
                .andExpect(jsonPath("$.data.myOrg.orgUnitName").value("프론트엔드팀"))
                .andExpect(jsonPath("$.data.myOrg.memberCount").value(2))
                .andExpect(jsonPath("$.data.myOrg.today.notCheckedIn").value(2))
                .andExpect(jsonPath("$.data.myOrg.leaveUsageThisMonth[0].leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.myOrg.leaveUsageThisMonth[0].days").value(2))
                .andExpect(jsonPath("$.data.company").doesNotExist());
        // 개발본부장 — 하위 조직 포함 4명, 윤은 재택 근무 중이고 지각 1
        home(org.kang)
                .andExpect(jsonPath("$.data.myOrg.memberCount").value(4))
                .andExpect(jsonPath("$.data.myOrg.today.working").value(1))
                .andExpect(jsonPath("$.data.myOrg.today.notCheckedIn").value(3))
                .andExpect(jsonPath("$.data.myOrg.lateCountThisMonth").value(1));
    }

    @Test
    void 회사_현황과_관리_배지는_권한대로() throws Exception {
        home(admin)
                .andExpect(jsonPath("$.data.company.headcount.active").value(7))
                .andExpect(jsonPath("$.data.company.headcount.resigned").value(1))
                .andExpect(jsonPath("$.data.company.headcount.byOrgUnit[*].orgUnitName").value(contains("개발본부")))
                .andExpect(jsonPath("$.data.company.headcount.byOrgUnit[0].count").value(4))
                .andExpect(jsonPath("$.data.company.headcount.byOrgUnit[0].children[*].count").value(contains(2, 1)))
                .andExpect(jsonPath("$.data.company.headcount.byEmploymentType[0].employmentTypeName").value("정규직"))
                .andExpect(jsonPath("$.data.company.headcount.byEmploymentType[0].count").value(7))
                .andExpect(jsonPath("$.data.company.onVacationThisMonth[0].employeeId").value(org.cho.id()))
                .andExpect(jsonPath("$.data.company.onVacationThisMonth[0].days").value(2))
                .andExpect(jsonPath("$.data.company.leaveUsageThisMonth[0].days").value(2))
                .andExpect(jsonPath("$.data.company.recentAssignments[0].employeeId").value(org.cho.id()))
                // 최고 관리자 — 세 배지 모두. 설정 미완료면 진행률
                .andExpect(jsonPath("$.data.admin.setupProgress.totalSteps").isNumber())
                .andExpect(jsonPath("$.data.admin.reassignNeeded").value(0))
                .andExpect(jsonPath("$.data.admin.attendanceCorrections").isNumber())
                .andExpect(jsonPath("$.data.myOrg").doesNotExist());

        // 설정을 마치면 setupProgress 는 null(필드는 있음)
        jdbc.update("UPDATE company SET setup_completed = TRUE WHERE id = ?", cid);
        home(admin).andExpect(jsonPath("$.data.admin.setupProgress").value(nullValue()));

        // 인사 담당 — DASHBOARD_COMPANY · ATTENDANCE_MANAGE 는 있고 APPROVAL_MANAGE · COMPANY_MANAGE 는 없다
        home(hr)
                .andExpect(jsonPath("$.data.company").exists())
                .andExpect(jsonPath("$.data.admin.attendanceCorrections").isNumber())
                .andExpect(jsonPath("$.data.admin.reassignNeeded").doesNotExist())
                .andExpect(jsonPath("$.data.admin.setupProgress").doesNotExist());
    }
}
