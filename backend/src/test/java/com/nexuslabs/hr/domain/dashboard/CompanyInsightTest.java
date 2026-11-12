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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 홈 전사 위젯 — 성과 현황(F-DASH-06) · 직원별 인건비(F-DASH-07).
 * DemoOrg: 최상위(대표 ceo) → 개발본부(강) → 프론트엔드팀(서 · 조) · 백엔드팀(윤). 여기에 영업팀 → 영업1팀을 더한다.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class CompanyInsightTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    long sales;
    long sales1;
    String rootName;
    TestFixture.Employee hr;
    TestFixture.Employee admin;
    long template;
    long q1;
    long q2;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(12, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        sales = fixture.orgUnit(cid, org.company.rootOrgUnitId(), "영업팀");
        sales1 = fixture.orgUnit(cid, sales, "영업1팀");
        rootName = jdbc.queryForObject("SELECT name FROM org_unit WHERE id = ?", String.class, org.company.rootOrgUnitId());
        hr = fixture.employee(cid, org.company.rootOrgUnitId(), "인사 담당", false);
        admin = new TestFixture.Employee(org.company.adminId(), cid, org.company.adminEmail());
        // 질문 2개 · 배점 100 → 총점 = (두 답의 합) × 10
        template = jdbc.queryForObject("INSERT INTO eval_template (company_id, name) VALUES (?, '일반') RETURNING id",
                Long.class, cid);
        long criteria = jdbc.queryForObject("""
                INSERT INTO eval_criteria (company_id, eval_template_id, category, name, weight, sort_order)
                VALUES (?, ?, '성과', '성과', 100, 1) RETURNING id""", Long.class, cid, template);
        q1 = question(criteria, 1);
        q2 = question(criteria, 2);
    }

    private long question(long criteria, int order) {
        return jdbc.queryForObject("""
                INSERT INTO eval_question (company_id, eval_criteria_id, content, sort_order) VALUES (?, ?, '질문', ?)
                RETURNING id""", Long.class, cid, criteria, order);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions home(TestFixture.Employee e) throws Exception {
        return as(e, get("/api/dashboard/home")).andExpect(status().isOk());
    }

    private long cycle(String name, String status, String start, String closedAt) {
        return jdbc.queryForObject("""
                INSERT INTO eval_cycle (company_id, name, start_date, end_date, status, started_at, closed_at)
                VALUES (?, ?, ?::date, ?::date + 30, ?::eval_cycle_status, now(), ?::timestamptz) RETURNING id""",
                Long.class, cid, name, start, start, status, closedAt);
    }

    /** 답 a · b 로 총점 (a + b) × 10. */
    private long evaluation(long cycle, long target, String status, int a, int b) {
        long id = jdbc.queryForObject("""
                INSERT INTO evaluation (company_id, eval_cycle_id, target_employee_id, evaluator_id, eval_template_id, status,
                                        confirmed_at)
                VALUES (?, ?, ?, ?, ?, ?::evaluation_status, CASE WHEN ? = 'CONFIRMED' THEN now() END) RETURNING id""",
                Long.class, cid, cycle, target, admin.id(), template, status, status);
        jdbc.update("INSERT INTO eval_answer (company_id, evaluation_id, eval_question_id, score) VALUES (?, ?, ?, ?)",
                cid, id, q1, a);
        jdbc.update("INSERT INTO eval_answer (company_id, evaluation_id, eval_question_id, score) VALUES (?, ?, ?, ?)",
                cid, id, q2, b);
        return id;
    }

    private void grade(long employeeId, String name) {
        jdbc.update("UPDATE employee SET job_grade_id = (SELECT id FROM job_grade WHERE company_id = ? AND name = ?) WHERE id = ?",
                cid, name, employeeId);
    }

    /** DASHBOARD_COMPANY 와 팀 범위 EVAL_READ 만 있는 역할의 직원. */
    private TestFixture.Employee dashboardOnly() {
        long role = jdbc.queryForObject(
                "INSERT INTO role (company_id, name, description, is_system) VALUES (?, '대시보드만', '', FALSE) RETURNING id",
                Long.class, cid);
        jdbc.update("""
                INSERT INTO role_permission (company_id, role_id, permission_code, scope)
                VALUES (?, ?, 'DASHBOARD_COMPANY', 'ALL'), (?, ?, 'EVAL_READ', 'TEAM')""", cid, role, cid, role);
        return fixture.employee(cid, org.company.rootOrgUnitId(), "대시보드만", false);
    }

    // ------------------------------------------------------------------ 성과 현황

    @Test
    void 성과_현황은_확정된_평가로_순위와_조직별_평균을_낸다() throws Exception {
        long closed = cycle("2029 하반기", "CLOSED", "2029-07-01", "2030-01-10 10:00+09");
        long[] sellers = {fixture.employee(cid, sales, "직원", false).id(), fixture.employee(cid, sales, "직원", false).id(),
                fixture.employee(cid, sales1, "직원", false).id()};
        grade(org.kang.id(), "부장");
        long top1 = evaluation(closed, org.kang.id(), "CONFIRMED", 5, 5);   // 100
        evaluation(closed, org.seo.id(), "CONFIRMED", 5, 4);                // 90
        evaluation(closed, org.cho.id(), "CONFIRMED", 4, 4);                // 80
        evaluation(closed, org.yoon.id(), "CONFIRMED", 4, 3);               // 70
        evaluation(closed, sellers[0], "CONFIRMED", 3, 3);                  // 60 (사번이 빠르다)
        long tie = evaluation(closed, sellers[1], "CONFIRMED", 3, 3);       // 60
        evaluation(closed, org.ceo.id(), "CONFIRMED", 2, 2);                // 40 — 최상위 소속
        long last = evaluation(closed, sellers[2], "CONFIRMED", 1, 1);      // 20 — 영업1팀 → 영업팀으로 묶인다
        evaluation(closed, hr.id(), "SUBMITTED", 5, 5);                     // 확정 전 — 빠진다
        TestFixture.Employee gone = fixture.employee(cid, org.backendTeam, "직원", false);
        evaluation(closed, gone.id(), "CONFIRMED", 5, 5);                   // 퇴직자 — 빠진다
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", gone.id());
        // 더 최근의 진행 중 기간(확정 1건) — 마감된 기간이 있으니 기본은 마감된 기간
        long running = cycle("2030 상반기", "IN_PROGRESS", "2030-01-15", null);
        evaluation(running, org.cho.id(), "CONFIRMED", 3, 2);
        cycle("확정 없음", "CLOSED", "2030-02-01", "2030-03-01 10:00+09");  // 확정 결과가 없는 기간 — 목록에 없다

        home(org.ceo)
                .andExpect(jsonPath("$.data.company.performance.evalCycleId").value(closed))
                .andExpect(jsonPath("$.data.company.performance.cycleName").value("2029 하반기"))
                .andExpect(jsonPath("$.data.company.performance.cycleKind").value("REGULAR"))
                .andExpect(jsonPath("$.data.company.performance.confirmedCount").value(8))
                .andExpect(jsonPath("$.data.company.performance.average").value(65.0))
                .andExpect(jsonPath("$.data.company.performance.levelCounts.VERY_GOOD").value(2))
                .andExpect(jsonPath("$.data.company.performance.levelCounts.GOOD").value(2))
                .andExpect(jsonPath("$.data.company.performance.levelCounts.NORMAL").value(2))
                .andExpect(jsonPath("$.data.company.performance.levelCounts.POOR").value(1))
                .andExpect(jsonPath("$.data.company.performance.levelCounts.VERY_POOR").value(1))
                .andExpect(jsonPath("$.data.company.performance.top[*].rank").value(contains(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$.data.company.performance.top[0].evaluationId").value(top1))
                .andExpect(jsonPath("$.data.company.performance.top[0].employeeId").value(org.kang.id()))
                .andExpect(jsonPath("$.data.company.performance.top[0].orgUnitName").value("개발본부"))
                .andExpect(jsonPath("$.data.company.performance.top[0].jobGradeName").value("부장"))
                .andExpect(jsonPath("$.data.company.performance.top[0].totalScore").value(100.0))
                .andExpect(jsonPath("$.data.company.performance.top[0].level").value("VERY_GOOD"))
                .andExpect(jsonPath("$.data.company.performance.top[4].employeeId").value(sellers[0]))
                // 꼴찌부터, 상위 5명과 겹치지 않게 — 8 · 7 · 6위
                .andExpect(jsonPath("$.data.company.performance.needsImprovement[*].rank").value(contains(8, 7, 6)))
                .andExpect(jsonPath("$.data.company.performance.needsImprovement[0].evaluationId").value(last))
                .andExpect(jsonPath("$.data.company.performance.needsImprovement[0].level").value("VERY_POOR"))
                .andExpect(jsonPath("$.data.company.performance.needsImprovement[2].evaluationId").value(tie))
                // 최상위 바로 아래 조직(하위 포함), 최상위 소속은 맨 뒤 한 줄. 3명 미만은 평균 null(키는 있음)
                .andExpect(jsonPath("$.data.company.performance.byOrgUnit[*].orgUnitName")
                        .value(contains("개발본부", "영업팀", rootName)))
                .andExpect(jsonPath("$.data.company.performance.byOrgUnit[*].count").value(contains(4, 3, 1)))
                .andExpect(jsonPath("$.data.company.performance.byOrgUnit[0].average").value(85.0))
                .andExpect(jsonPath("$.data.company.performance.byOrgUnit[1].average").value(46.7))
                .andExpect(jsonPath("$.data.company.performance.byOrgUnit[2].average").value(nullValue()))
                .andExpect(jsonPath("$.data.company.performance.availableCycles").doesNotExist());

        as(org.ceo, get("/api/dashboard/company/performance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evalCycleId").value(closed))
                .andExpect(jsonPath("$.data.availableCycles[*].evalCycleId").value(contains((int) running, (int) closed)))
                .andExpect(jsonPath("$.data.availableCycles[1].cycleKind").value("REGULAR"))
                .andExpect(jsonPath("$.data.availableCycles[1].closedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.availableCycles[0].closedAt").value(nullValue()));
        as(org.ceo, get("/api/dashboard/company/performance").param("evalCycleId", String.valueOf(running)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cycleName").value("2030 상반기"))
                .andExpect(jsonPath("$.data.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.average").value(50.0))
                .andExpect(jsonPath("$.data.needsImprovement.length()").value(0))
                .andExpect(jsonPath("$.data.levelCounts.VERY_GOOD").value(0));

        // 없는 기간 · 다른 회사 기간은 404
        long otherCompany = fixture.company("남의회사").id();
        long foreign = jdbc.queryForObject("""
                INSERT INTO eval_cycle (company_id, name, start_date, end_date, status) VALUES (?, '남의 기간', '2030-01-01',
                '2030-01-31', 'CLOSED') RETURNING id""", Long.class, otherCompany);
        as(org.ceo, get("/api/dashboard/company/performance").param("evalCycleId", String.valueOf(foreign)))
                .andExpect(status().isNotFound());
        as(org.ceo, get("/api/dashboard/company/performance").param("evalCycleId", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 마감된_기간이_없으면_진행_중인_기간_확정이_하나도_없으면_성과_키가_없다() throws Exception {
        home(org.ceo).andExpect(jsonPath("$.data.company.performance").doesNotExist());
        as(org.ceo, get("/api/dashboard/company/performance")).andExpect(status().isNotFound());

        long running = cycle("2030 상반기", "IN_PROGRESS", "2030-01-15", null);
        evaluation(running, org.cho.id(), "CONFIRMED", 4, 4);
        home(org.ceo).andExpect(jsonPath("$.data.company.performance.evalCycleId").value(running))
                .andExpect(jsonPath("$.data.company.performance.byOrgUnit[0].average").value(nullValue()));
    }

    // ------------------------------------------------------------------ 직원별 인건비

    private long run(String month, String status) {
        return jdbc.queryForObject("""
                INSERT INTO payroll_run (company_id, pay_month, pay_date, status, company_name_snap, ceo_name_snap,
                                         business_reg_no_snap, company_address_snap, confirmed_by,
                                         cancelled_by, cancelled_at, cancel_reason)
                VALUES (?, ?, (? || '-25')::date, ?::payroll_status, '데모랩스', '대표', '1234567890', '서울', ?,
                        CASE WHEN ? = 'CANCELLED' THEN ?::bigint END, CASE WHEN ? = 'CANCELLED' THEN now() END,
                        CASE WHEN ? = 'CANCELLED' THEN '다시 정산' END)
                RETURNING id""",
                Long.class, cid, month, month, status, admin.id(), status, admin.id(), status, status);
    }

    private void stub(long run, long employeeId, long orgSnap, long gross, long burden) {
        jdbc.update("""
                INSERT INTO paystub (company_id, payroll_run_id, employee_id, employee_no_snap, employee_name_snap,
                                     org_unit_id_snap, org_name_snap, base_pay, ordinary_hourly_wage, dependents_count,
                                     children_count, worked_days, month_days, gross_pay, taxable_pay, income_tax,
                                     local_income_tax, total_deduction, net_pay, company_burden_total)
                SELECT ?, ?, e.id, e.employee_no, e.name, o.id, o.name, ?, 0, 1, 0, 28, 28, ?, ?, 0, 0, 0, ?, ?
                FROM employee e, org_unit o WHERE e.id = ? AND o.id = ?""",
                cid, run, gross, gross, gross, gross, burden, employeeId, orgSnap);
    }

    @Test
    void 직원별_인건비는_최근_정산_월의_명세서로_센다() throws Exception {
        long jan = run("2030-01", "CONFIRMED");
        stub(jan, org.kang.id(), org.devDivision, 60_000_000, 6_000_000);                 // 직전 합계 66,000,000
        long cancelled = run("2030-03", "CANCELLED");                                      // 취소 — 가장 최근이어도 빠진다
        stub(cancelled, org.kang.id(), org.devDivision, 900_000_000, 0);
        long feb = run("2030-02", "CONFIRMED");
        // i = 0..10 → 인건비 (i + 1) × 1,100,000, 합계 72,600,000
        List<Long> people = new java.util.ArrayList<>(List.of(org.ceo.id(), hr.id(), org.kang.id(), org.seo.id(),
                org.cho.id(), org.yoon.id()));
        for (int i = 0; i < 5; i++) {
            people.add(fixture.employee(cid, sales, "직원", false).id());
        }
        long[] snaps = {org.company.rootOrgUnitId(), org.company.rootOrgUnitId(), org.devDivision, org.frontendTeam,
                org.frontendTeam, org.backendTeam, sales, sales, sales1, sales, sales};
        for (int i = 0; i < people.size(); i++) {
            stub(feb, people.get(i), snaps[i], (i + 1) * 1_000_000L, (i + 1) * 100_000L);
        }
        long best = people.get(10);
        grade(best, "대리");
        // 정산 뒤 다른 조직으로 옮겨도 정산 당시 소속으로 센다
        jdbc.update("UPDATE employee SET org_unit_id = ? WHERE id = ?", org.backendTeam, best);

        home(org.ceo)
                .andExpect(jsonPath("$.data.company.laborCost.payMonth").value("2030-02"))
                .andExpect(jsonPath("$.data.company.laborCost.headcount").value(11))
                .andExpect(jsonPath("$.data.company.laborCost.total").value(72_600_000))
                .andExpect(jsonPath("$.data.company.laborCost.changeRate").value(10.0))
                .andExpect(jsonPath("$.data.company.laborCost.top.length()").value(10))
                .andExpect(jsonPath("$.data.company.laborCost.top[0].employeeId").value(best))
                .andExpect(jsonPath("$.data.company.laborCost.top[0].orgUnitName").value("영업팀"))
                .andExpect(jsonPath("$.data.company.laborCost.top[0].jobGradeName").value("대리"))
                .andExpect(jsonPath("$.data.company.laborCost.top[0].grossPay").value(11_000_000))
                .andExpect(jsonPath("$.data.company.laborCost.top[0].companyBurden").value(1_100_000))
                .andExpect(jsonPath("$.data.company.laborCost.top[0].laborCost").value(12_100_000))
                .andExpect(jsonPath("$.data.company.laborCost.top[0].share").value(16.7))
                .andExpect(jsonPath("$.data.company.laborCost.top[9].employeeId").value(hr.id()))
                .andExpect(jsonPath("$.data.company.laborCost.byOrgUnit[*].orgUnitName")
                        .value(contains("개발본부", "영업팀", rootName)))
                .andExpect(jsonPath("$.data.company.laborCost.byOrgUnit[*].headcount").value(contains(4, 5, 2)))
                .andExpect(jsonPath("$.data.company.laborCost.byOrgUnit[*].laborCost")
                        .value(contains(19_800_000, 49_500_000, 3_300_000)))
                .andExpect(jsonPath("$.data.company.laborCost.employees").doesNotExist())
                .andExpect(jsonPath("$.data.company.laborCost.availableMonths").doesNotExist());

        as(org.ceo, get("/api/dashboard/company/labor-cost")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payMonth").value("2030-02"))
                .andExpect(jsonPath("$.data.total").value(72_600_000))
                .andExpect(jsonPath("$.data.top").doesNotExist())
                .andExpect(jsonPath("$.data.employees.size").value(20))
                .andExpect(jsonPath("$.data.employees.totalElements").value(11))
                .andExpect(jsonPath("$.data.employees.content[0].employeeId").value(best))
                .andExpect(jsonPath("$.data.availableMonths").value(contains("2030-02", "2030-01")));
        as(org.ceo, get("/api/dashboard/company/labor-cost").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employees.page").value(1))
                .andExpect(jsonPath("$.data.employees.totalPages").value(4))
                .andExpect(jsonPath("$.data.employees.content[*].employeeId")
                        .value(contains(people.get(7).intValue(), people.get(6).intValue(), people.get(5).intValue())));
        as(org.ceo, get("/api/dashboard/company/labor-cost").param("size", "500"))
                .andExpect(jsonPath("$.data.employees.size").value(100));
        // 직전 정산이 없으면 changeRate 는 null(키는 있음)
        as(org.ceo, get("/api/dashboard/company/labor-cost").param("payMonth", "2030-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headcount").value(1))
                .andExpect(jsonPath("$.data.changeRate").value(nullValue()));
        // 취소된 달 · 정산이 없는 달은 PAYROLL_NOT_FOUND
        as(org.ceo, get("/api/dashboard/company/labor-cost").param("payMonth", "2030-03"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYROLL_NOT_FOUND"));
        as(org.ceo, get("/api/dashboard/company/labor-cost").param("payMonth", "2029-05"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 정산이_없으면_인건비_키가_없고_기간_API_는_404() throws Exception {
        home(org.ceo).andExpect(jsonPath("$.data.company.laborCost").doesNotExist());
        as(org.ceo, get("/api/dashboard/company/labor-cost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYROLL_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ 권한

    @Test
    void 권한이_모자라면_키가_없고_기간_API_는_403() throws Exception {
        long closed = cycle("2029 하반기", "CLOSED", "2029-07-01", "2030-01-10 10:00+09");
        evaluation(closed, org.cho.id(), "CONFIRMED", 4, 4);
        long feb = run("2030-02", "CONFIRMED");
        stub(feb, org.kang.id(), org.devDivision, 3_000_000, 300_000);

        // 인사 담당(전사 EVAL_READ · PAYROLL_READ) — 둘 다
        home(hr).andExpect(jsonPath("$.data.company.performance").exists())
                .andExpect(jsonPath("$.data.company.laborCost").exists());
        // DASHBOARD_COMPANY 와 팀 범위 EVAL_READ 만 — company 는 있고 두 키는 없다
        TestFixture.Employee viewer = dashboardOnly();
        home(viewer).andExpect(jsonPath("$.data.company.headcount").exists())
                .andExpect(jsonPath("$.data.company.performance").doesNotExist())
                .andExpect(jsonPath("$.data.company.laborCost").doesNotExist());
        as(viewer, get("/api/dashboard/company/performance")).andExpect(status().isForbidden());
        as(viewer, get("/api/dashboard/company/labor-cost")).andExpect(status().isForbidden());
        // 일반 직원(DASHBOARD_COMPANY 없음)
        as(org.cho, get("/api/dashboard/company/performance")).andExpect(status().isForbidden());
        as(org.cho, get("/api/dashboard/company/labor-cost")).andExpect(status().isForbidden());
    }
}
