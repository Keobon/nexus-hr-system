package com.nexuslabs.hr.domain.company;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-COMP-03 초기 설정 마법사 상태 (API 설계서 3장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanySetupTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("마법사테스트");
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    @Test
    void 회사를_등록한_직후에는_기본값이_있는_단계만_done_이다() throws Exception {
        asAdmin(get("/api/company/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupCompleted").value(false))
                .andExpect(jsonPath("$.data.steps[*].step").value(contains("ORG_UNITS", "JOB_GRADES_TITLES",
                        "EMPLOYMENT_TYPES", "WORK_SCHEDULE_HOLIDAYS", "LEAVE_TYPES", "PAY_ITEMS", "EMPLOYEES")))
                // 최상위 조직과 등록한 관리자는 세지 않는다
                .andExpect(jsonPath("$.data.steps[0].counts.orgUnits").value(0))
                .andExpect(jsonPath("$.data.steps[0].done").value(false))
                .andExpect(jsonPath("$.data.steps[1].counts.jobGrades").value(0))
                .andExpect(jsonPath("$.data.steps[1].counts.jobTitles").value(0))
                .andExpect(jsonPath("$.data.steps[1].done").value(false))
                .andExpect(jsonPath("$.data.steps[2].counts.employmentTypes").value(3))
                .andExpect(jsonPath("$.data.steps[2].done").value(true))
                .andExpect(jsonPath("$.data.steps[3].counts.workSchedules").value(1))
                .andExpect(jsonPath("$.data.steps[3].counts.holidays").value(0))
                .andExpect(jsonPath("$.data.steps[3].done").value(true))
                .andExpect(jsonPath("$.data.steps[4].counts.leaveTypes").value(3))
                .andExpect(jsonPath("$.data.steps[4].done").value(true))
                .andExpect(jsonPath("$.data.steps[5].counts.payItems").value(10))
                .andExpect(jsonPath("$.data.steps[5].done").value(true))
                .andExpect(jsonPath("$.data.steps[6].counts.employees").value(1))
                .andExpect(jsonPath("$.data.steps[6].done").value(false));
    }

    @Test
    void 단계의_데이터를_만들면_개수와_done_이_바뀐다() throws Exception {
        long team = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "개발팀");
        long closed = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "폐지팀");
        jdbc.update("UPDATE org_unit SET is_active = FALSE WHERE id = ?", closed);
        jdbc.update("INSERT INTO job_grade (company_id, name, sort_order) VALUES (?, ?, 1), (?, ?, 2)",
                company.id(), "사원", company.id(), "대리");
        jdbc.update("INSERT INTO job_title (company_id, name, sort_order) VALUES (?, ?, 1)", company.id(), "팀장");
        long resigned = fixture.employee(company.id(), team, "직원", false).id();
        jdbc.update("UPDATE employee SET status = ?::emp_status WHERE id = ?", "RESIGNED", resigned);
        fixture.employee(company.id(), team, "직원", false);

        asAdmin(get("/api/company/setup"))
                // 비활성 조직과 퇴직자는 세지 않는다
                .andExpect(jsonPath("$.data.steps[0].counts.orgUnits").value(1))
                .andExpect(jsonPath("$.data.steps[0].done").value(true))
                .andExpect(jsonPath("$.data.steps[1].counts.jobGrades").value(2))
                .andExpect(jsonPath("$.data.steps[1].counts.jobTitles").value(1))
                .andExpect(jsonPath("$.data.steps[1].done").value(true))
                .andExpect(jsonPath("$.data.steps[6].counts.employees").value(2))
                .andExpect(jsonPath("$.data.steps[6].done").value(true));
    }

    @Test
    void 설정_완료는_단계를_건너뛰어도_되고_두_번_눌러도_된다() throws Exception {
        asAdmin(post("/api/company/setup/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupCompleted").value(true))
                .andExpect(jsonPath("$.data.steps[0].done").value(false));
        assertThat(jdbc.queryForObject("SELECT setup_completed FROM company WHERE id = ?", Boolean.class,
                company.id())).isTrue();

        asAdmin(post("/api/company/setup/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupCompleted").value(true));
        asAdmin(get("/api/company/setup")).andExpect(jsonPath("$.data.setupCompleted").value(true));
        // 화면이 마법사 이동 여부를 판단하는 /me 에도 바로 반영된다
        asAdmin(get("/api/me")).andExpect(jsonPath("$.data.company.setupCompleted").value(true));
    }

    @Test
    void 마법사_상태와_완료는_COMPANY_MANAGE_가_있어야_한다() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        String staffToken = fixture.token(staff.id(), company.id());

        mvc.perform(get("/api/company/setup").header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post("/api/company/setup/complete").header("Authorization", staffToken))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT setup_completed FROM company WHERE id = ?", Boolean.class,
                company.id())).isFalse();
    }

    @Test
    void 다른_회사의_설정_상태와_섞이지_않는다() throws Exception {
        fixture.orgUnit(company.id(), company.rootOrgUnitId(), "개발팀");
        asAdmin(post("/api/company/setup/complete")).andExpect(status().isOk());
        TestFixture.Company other = fixture.company("마법사테스트타사");

        mvc.perform(get("/api/company/setup").header("Authorization", fixture.token(other.adminId(), other.id())))
                .andExpect(jsonPath("$.data.setupCompleted").value(false))
                .andExpect(jsonPath("$.data.steps[0].counts.orgUnits").value(0));
    }
}
