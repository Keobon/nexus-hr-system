package com.nexuslabs.hr.domain.employee;

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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전 직원 연락처(10/15 시연 피드백) — 인사 담당 등 전사 직원 조회 권한이 있거나 조직장이면 전 직원의 연락처를 본다.
 * EmployeeListTest 와 같이 @Transactional 없이 새 회사를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeContactsTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    long dev;
    long team;
    long biz;
    long devLead;
    long developer;
    long seller;
    long hr;

    /** 회사(관리자) — 개발본부(본부장 강하늘) — 백엔드팀(임태현) / 사업본부(신유나) · 인사 담당 정수아. */
    @BeforeEach
    void setUp() {
        company = fixture.company("연락처테스트");
        dev = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "개발본부");
        team = fixture.orgUnit(company.id(), dev, "백엔드팀");
        biz = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "사업본부");
        devLead = member(dev, "강하늘", "직원");
        developer = member(team, "임태현", "직원");
        seller = member(biz, "신유나", "직원");
        hr = member(biz, "정수아", "인사 담당");
        fixture.lead(company.id(), dev, devLead);
        jdbc.update("""
                UPDATE employee SET phone = '010-1111-2222', address = '서울시 중구',
                       job_grade_id = (SELECT id FROM job_grade WHERE company_id = ? AND name = '대리')
                WHERE id = ?""", company.id(), seller);
    }

    private long member(long orgUnitId, String name, String roleName) {
        long id = fixture.employee(company.id(), orgUnitId, roleName, false).id();
        jdbc.update("UPDATE employee SET name = ? WHERE id = ?", name, id);
        return id;
    }

    private ResultActions as(long employeeId, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", fixture.token(employeeId, company.id())));
    }

    @Test
    void 인사_담당은_전_직원의_이름_직급_소속과_연락처를_본다() throws Exception {
        String sellerEmail = jdbc.queryForObject("SELECT email FROM employee WHERE id = ?", String.class, seller);
        as(hr, "/api/employees/contacts?keyword=신유나")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(seller))
                .andExpect(jsonPath("$.data.content[0].name").value("신유나"))
                .andExpect(jsonPath("$.data.content[0].orgUnitName").value("사업본부"))
                .andExpect(jsonPath("$.data.content[0].jobGradeName").value("대리"))
                .andExpect(jsonPath("$.data.content[0].email").value(sellerEmail))
                .andExpect(jsonPath("$.data.content[0].phone").value("010-1111-2222"))
                .andExpect(jsonPath("$.data.content[0].address").value("서울시 중구"));
    }

    @Test
    void 조직장은_자기_팀_밖의_직원까지_전_직원을_본다() throws Exception {
        as(devLead, "/api/employees/contacts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5)) // 관리자 포함
                .andExpect(jsonPath("$.data.content[*].name", hasItems("신유나", "정수아", "임태현", "강하늘")));
    }

    @Test
    void 조직장이_아닌_일반_직원은_볼_수_없다() throws Exception {
        as(developer, "/api/employees/contacts")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 퇴직자는_빠지고_휴직자는_나온다() throws Exception {
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", developer);
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", seller);
        as(hr, "/api/employees/contacts?orgUnitId=" + company.rootOrgUnitId())
                .andExpect(jsonPath("$.data.content[*].name", not(hasItem("임태현"))))
                .andExpect(jsonPath("$.data.content[*].name", hasItem("신유나")));
    }

    @Test
    void 부서를_고르면_하위_조직까지_나오고_다른_회사_직원은_섞이지_않는다() throws Exception {
        TestFixture.Company other = fixture.company("연락처다른회사");
        long outsider = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false).id();
        jdbc.update("UPDATE employee SET name = '외부인' WHERE id = ?", outsider);

        as(hr, "/api/employees/contacts?orgUnitId=" + dev)
                .andExpect(jsonPath("$.data.content[*].name", containsInAnyOrder("강하늘", "임태현")));
        as(hr, "/api/employees/contacts?keyword=외부인")
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 사원번호_순으로_나온다() throws Exception {
        as(hr, "/api/employees/contacts?orgUnitId=" + dev)
                .andExpect(jsonPath("$.data.content[*].name", contains("강하늘", "임태현")));
    }
}
