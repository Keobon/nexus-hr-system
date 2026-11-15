package com.nexuslabs.hr.domain.turnover;

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

import java.sql.Date;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 퇴사 통계(작업 지시서 2-3 · 2-4). 손으로 계산할 수 있는 작은 고정 데이터로 계산식을 확인한다.
 * <pre>
 * 최상위 ── A본부 ── A팀       B팀
 * 최상위: 관리자 · 인사 담당 · 경영진(모두 2020-01-01 입사, 재직)
 * A본부 : e1(2020-01-01, 재직)          A팀: 조직장 lead(2020-01-01, 재직)
 *         e2 "이수"   2024-06-01 → 2025-02-01 개인 사정     e3 "홍길동" 2022-01-10 → 2025-03-31 이직
 * B팀   : e4 2025-02-10 입사(재직)  e5 2023-05-01 → 2025-01-01 이직
 *         e6 2018-01-01 → 2024-12-31 기타   e7 2019-01-01 → 2025-04-01 정년
 * 재직(D) = 입사일 <= D 이고 (퇴사일 없음 또는 퇴사일 > D)
 * </pre>
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnoverStatsTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    long cid;
    long aDivision;
    long aTeam;
    long bTeam;
    String rootName;
    TestFixture.Employee hr;
    TestFixture.Employee exec;
    TestFixture.Employee lead;
    TestFixture.Employee e2;
    TestFixture.Employee e3;
    TestFixture.Employee e5;

    @BeforeEach
    void setUp() {
        TestFixture.Company company = fixture.company("퇴사통계");
        cid = company.id();
        long root = company.rootOrgUnitId();
        rootName = jdbc.queryForObject("SELECT name FROM org_unit WHERE id = ?", String.class, root);
        aDivision = fixture.orgUnit(cid, root, "A본부");
        aTeam = fixture.orgUnit(cid, aDivision, "A팀");
        bTeam = fixture.orgUnit(cid, root, "B팀");
        hr = fixture.employee(cid, root, "인사 담당", false);
        exec = fixture.employee(cid, root, "경영진", false);
        lead = fixture.employee(cid, aTeam, "직원", false);
        fixture.lead(cid, aTeam, lead.id());
        fixture.employee(cid, aDivision, "직원", false);                         // e1(재직)
        e2 = fixture.employee(cid, aTeam, "직원", false);
        e3 = fixture.employee(cid, aTeam, "직원", false);
        TestFixture.Employee e4 = fixture.employee(cid, bTeam, "직원", false);
        e5 = fixture.employee(cid, bTeam, "직원", false);
        TestFixture.Employee e6 = fixture.employee(cid, bTeam, "직원", false);
        TestFixture.Employee e7 = fixture.employee(cid, bTeam, "직원", false);

        jdbc.update("UPDATE employee SET hire_date = '2020-01-01' WHERE company_id = ?", cid);
        hire(e2, "2024-06-01");
        hire(e3, "2022-01-10");
        hire(e4, "2025-02-10");
        hire(e5, "2023-05-01");
        hire(e6, "2018-01-01");
        hire(e7, "2019-01-01");
        resign(e2, "2025-02-01", "PERSONAL");
        resign(e3, "2025-03-31", "JOB_CHANGE");
        resign(e5, "2025-01-01", "JOB_CHANGE");
        resign(e6, "2024-12-31", "ETC");
        resign(e7, "2025-04-01", "RETIREMENT");
        jdbc.update("UPDATE employee SET name = '이수' WHERE id = ?", e2.id());
        jdbc.update("UPDATE employee SET name = '홍길동' WHERE id = ?", e3.id());
        jdbc.update("UPDATE employee SET job_grade_id = (SELECT id FROM job_grade WHERE company_id = ? AND name = '대리') WHERE id = ?",
                cid, e3.id());
    }

    private void hire(TestFixture.Employee e, String date) {
        jdbc.update("UPDATE employee SET hire_date = ? WHERE id = ?", Date.valueOf(LocalDate.parse(date)), e.id());
    }

    private void resign(TestFixture.Employee e, String date, String reason) {
        jdbc.update("""
                UPDATE employee SET status = 'RESIGNED', resign_date = ?, resign_reason = ?::resign_reason WHERE id = ?""",
                Date.valueOf(LocalDate.parse(date)), reason, e.id());
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions ok(TestFixture.Employee e, String path, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/stats/turnover/" + path);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return as(e, request).andExpect(status().isOk());
    }

    @Test
    void 월_요약은_말일_재직_입사_퇴사_퇴사율_전월_퇴사율_순증감() throws Exception {
        // 2월: 2/1 재직 7 · 2/28 재직 8 → 평균 7.5, 퇴사 e2(2/1) 1명 → 13.3%. 1월: 8 · 8 → 평균 8, 퇴사 e5 1명 → 12.5%
        ok(hr, "summary", "month", "2025-02")
                .andExpect(jsonPath("$.data.month").value("2025-02"))
                .andExpect(jsonPath("$.data.headcount").value(8))
                .andExpect(jsonPath("$.data.hires").value(1))
                .andExpect(jsonPath("$.data.resignations").value(1))
                .andExpect(jsonPath("$.data.turnoverRate").value(13.3))
                .andExpect(jsonPath("$.data.prevTurnoverRate").value(12.5))
                .andExpect(jsonPath("$.data.netChange").value(0));
    }

    @Test
    void 추이는_월_분기_연_구간마다_첫날_말일로_오래된_순() throws Exception {
        ok(hr, "trend", "unit", "month", "from", "2025-01", "to", "2025-03")
                .andExpect(jsonPath("$.data[*].period").value(contains("2025-01", "2025-02", "2025-03")))
                .andExpect(jsonPath("$.data[*].hires").value(contains(0, 1, 0)))
                .andExpect(jsonPath("$.data[*].resignations").value(contains(1, 1, 1)))
                .andExpect(jsonPath("$.data[*].avgHeadcount").value(contains(8.0, 7.5, 7.5)))
                .andExpect(jsonPath("$.data[*].turnoverRate").value(contains(12.5, 13.3, 13.3)));
        // 2024-Q4: 10/1 재직 10 · 12/31 재직 9(e6 퇴사일 12/31) → 9.5, 퇴사 1 → 10.5% / 2025-Q1: 8 · 7 → 7.5, 퇴사 3 → 40.0%
        ok(hr, "trend", "unit", "quarter", "from", "2024-12", "to", "2025-03")
                .andExpect(jsonPath("$.data[*].period").value(contains("2024-Q4", "2025-Q1")))
                .andExpect(jsonPath("$.data[*].avgHeadcount").value(contains(9.5, 7.5)))
                .andExpect(jsonPath("$.data[*].resignations").value(contains(1, 3)))
                .andExpect(jsonPath("$.data[*].turnoverRate").value(contains(10.5, 40.0)));
        // 연 단위는 그해 1/1 · 12/31 — 8 · 6 → 7.0, 퇴사 4(e7 4/1 포함) → 57.1%
        ok(hr, "trend", "unit", "year", "from", "2025-01", "to", "2025-03")
                .andExpect(jsonPath("$.data[*].period").value(contains("2025")))
                .andExpect(jsonPath("$.data[0].avgHeadcount").value(7.0))
                .andExpect(jsonPath("$.data[0].hires").value(1))
                .andExpect(jsonPath("$.data[0].resignations").value(4))
                .andExpect(jsonPath("$.data[0].turnoverRate").value(57.1));
        // 데이터가 없는 기간도 0으로 채운다(평균 0이면 퇴사율 0)
        ok(hr, "trend", "unit", "month", "from", "2010-01", "to", "2010-02")
                .andExpect(jsonPath("$.data[*].period").value(contains("2010-01", "2010-02")))
                .andExpect(jsonPath("$.data[*].turnoverRate").value(contains(0.0, 0.0)));
    }

    @Test
    void 부서별_근속별_사유별_퇴사자_목록() throws Exception {
        // 최상위 바로 아래 조직(하위 포함), 최상위 직속은 맨 뒤
        ok(hr, "by-department", "from", "2025-01", "to", "2025-03")
                .andExpect(jsonPath("$.data[*].orgUnitName").value(contains("A본부", "B팀", rootName)))
                .andExpect(jsonPath("$.data[*].avgHeadcount").value(contains(3.0, 1.5, 3.0)))
                .andExpect(jsonPath("$.data[*].resignations").value(contains(2, 1, 0)))
                .andExpect(jsonPath("$.data[*].turnoverRate").value(contains(66.7, 66.7, 0.0)));
        ok(hr, "by-tenure", "from", "2025-01", "to", "2025-03")
                .andExpect(jsonPath("$.data[*].bucket").value(contains("UNDER_1Y", "Y1_3", "Y3_5", "OVER_5Y")))
                .andExpect(jsonPath("$.data[*].count").value(contains(1, 1, 1, 0)));
        ok(hr, "by-reason", "from", "2025-01", "to", "2025-03")
                .andExpect(jsonPath("$.data[*].reason").value(contains("JOB_CHANGE", "PERSONAL")))
                .andExpect(jsonPath("$.data[*].count").value(contains(2, 1)))
                .andExpect(jsonPath("$.data[*].ratio").value(contains(66.7, 33.3)));
        ok(hr, "resignations", "from", "2025-01", "to", "2025-03")
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[*].employeeId").value(contains((int) e3.id(), (int) e2.id(), (int) e5.id())))
                .andExpect(jsonPath("$.data.content[0].name").value("홍길동"))
                .andExpect(jsonPath("$.data.content[0].orgUnitName").value("A팀"))
                .andExpect(jsonPath("$.data.content[0].jobGradeName").value("대리"))
                .andExpect(jsonPath("$.data.content[0].hireDate").value("2022-01-10"))
                .andExpect(jsonPath("$.data.content[0].resignDate").value("2025-03-31"))
                .andExpect(jsonPath("$.data.content[0].tenureDays").value(1176))
                .andExpect(jsonPath("$.data.content[0].resignReason").value("JOB_CHANGE"));
        ok(hr, "resignations", "from", "2025-01", "to", "2025-03", "page", "1", "size", "2")
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[*].employeeId").value(contains((int) e5.id())));
        // 데이터가 없는 기간은 404 가 아니라 빈 값
        ok(hr, "by-department", "from", "2010-01", "to", "2010-02").andExpect(jsonPath("$.data.length()").value(0));
        ok(hr, "by-reason", "from", "2010-01", "to", "2010-02").andExpect(jsonPath("$.data.length()").value(0));
        ok(hr, "by-tenure", "from", "2010-01", "to", "2010-02").andExpect(jsonPath("$.data[*].count").value(contains(0, 0, 0, 0)));
        ok(hr, "resignations", "from", "2010-01", "to", "2010-02").andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 경영진은_이름이_가려지고_직원_조직장은_403() throws Exception {
        ok(exec, "resignations", "from", "2025-01", "to", "2025-03")
                .andExpect(jsonPath("$.data.content[0].name").value("홍*동"))
                .andExpect(jsonPath("$.data.content[1].name").value("이*"));
        ok(exec, "summary", "month", "2025-02").andExpect(jsonPath("$.data.headcount").value(8));
        TestFixture.Employee staff = fixture.employee(cid, bTeam, "직원", false);
        for (String path : new String[]{"summary?month=2025-02", "trend?unit=month&from=2025-01&to=2025-03",
                "by-department?from=2025-01&to=2025-03", "by-tenure?from=2025-01&to=2025-03",
                "by-reason?from=2025-01&to=2025-03", "resignations?from=2025-01&to=2025-03"}) {
            as(lead, get("/api/stats/turnover/" + path)).andExpect(status().isForbidden());
            as(staff, get("/api/stats/turnover/" + path)).andExpect(status().isForbidden());
        }
    }

    @Test
    void 기간이_거꾸로거나_단위가_틀리면_400() throws Exception {
        as(hr, get("/api/stats/turnover/trend").param("unit", "month").param("from", "2025-03").param("to", "2025-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        as(hr, get("/api/stats/turnover/trend").param("unit", "week").param("from", "2025-01").param("to", "2025-03"))
                .andExpect(status().isBadRequest());
        as(hr, get("/api/stats/turnover/by-reason").param("from", "2025-13").param("to", "2025-03"))
                .andExpect(status().isBadRequest());
    }
}
