package com.nexuslabs.hr.domain.assignment;

import com.jayway.jsonpath.JsonPath;
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

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ASSIGN-01–04 인사발령 (API 설계서 11장). DemoOrg: 개발본부(강하늘) 아래 프론트엔드팀(조직장 서예린 · 조현우),
 * 백엔드팀(조직장 윤). 시계는 2030-03-04(월) 09:00 — 발효일은 등록일로 고정(BR-ASSIGN-004).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class AssignmentTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    TestFixture.Employee hr;
    long staff, senior;   // 직급 사원 · 대리

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        hr = fixture.employee(cid, org.company.rootOrgUnitId(), "인사 담당", false);
        staff = grade("사원", 1);
        senior = grade("대리", 2);
        jdbc.update("UPDATE employee SET job_grade_id = ? WHERE company_id = ?", staff, cid);
    }

    private long grade(String name, int order) {
        return jdbc.queryForObject("INSERT INTO job_grade (company_id, name, sort_order) VALUES (?, ?, ?) RETURNING id",
                Long.class, cid, name, order);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions assign(TestFixture.Employee actor, long employeeId, String type, long orgUnitId,
                                 long gradeId, Long titleId) throws Exception {
        return as(actor, post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content("""
                {"employeeId": %d, "assignmentType": "%s", "toOrgUnitId": %d, "toJobGradeId": %d,
                 "toJobTitleId": %s, "reason": "조직 개편"}""".formatted(employeeId, type, orgUnitId, gradeId, titleId)));
    }

    private long assignOk(long employeeId, String type, long orgUnitId, long gradeId) throws Exception {
        String body = assign(hr, employeeId, type, orgUnitId, gradeId, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions correct(long assignmentId, long orgUnitId, long gradeId) throws Exception {
        return as(hr, post("/api/assignments/" + assignmentId + "/corrections").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"toOrgUnitId": %d, "toJobGradeId": %d, "toJobTitleId": null, "reason": "잘못 입력"}"""
                        .formatted(orgUnitId, gradeId)));
    }

    private long current(String column, long employeeId) {
        Long value = jdbc.queryForObject("SELECT " + column + " FROM employee WHERE id = ?", Long.class, employeeId);
        return value == null ? 0 : value;
    }

    private Long lead(long orgUnitId) {
        return jdbc.queryForObject("SELECT lead_employee_id FROM org_unit WHERE id = ?", Long.class, orgUnitId);
    }

    @Test
    void 발령은_변경_전_값을_서버가_채우고_현재_값을_바꾸고_떠난_조직의_조직장을_해제한다() throws Exception {
        assign(hr, org.seo.id(), "TRANSFER", org.backendTeam, senior, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeId").value(org.seo.id()))
                .andExpect(jsonPath("$.data.assignmentType").value("TRANSFER"))
                .andExpect(jsonPath("$.data.effectiveDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.fromOrgUnitName").value("프론트엔드팀"))
                .andExpect(jsonPath("$.data.toOrgUnitName").value("백엔드팀"))
                .andExpect(jsonPath("$.data.fromJobGradeName").value("사원"))
                .andExpect(jsonPath("$.data.toJobGradeName").value("대리"))
                .andExpect(jsonPath("$.data.fromJobTitleId").value(nullValue()))
                .andExpect(jsonPath("$.data.reason").value("조직 개편"))
                .andExpect(jsonPath("$.data.correctionOfId").value(nullValue()))
                .andExpect(jsonPath("$.data.correctedById").value(nullValue()))
                .andExpect(jsonPath("$.data.createdById").value(hr.id()));

        assertThat(current("org_unit_id", org.seo.id())).isEqualTo(org.backendTeam);
        assertThat(current("job_grade_id", org.seo.id())).isEqualTo(senior);
        // 서예린이 떠난 프론트엔드팀은 조직장 없음, 백엔드팀 조직장(윤)은 그대로
        assertThat(lead(org.frontendTeam)).isNull();
        assertThat(lead(org.backendTeam)).isEqualTo(org.yoon.id());
    }

    @Test
    void 발령_거부_규칙() throws Exception {
        // 바뀌는 값 없음
        assign(hr, org.cho.id(), "PROMOTION", org.frontendTeam, staff, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        // 비활성 조직으로
        long closed = fixture.orgUnit(cid, org.devDivision, "폐지팀");
        jdbc.update("UPDATE org_unit SET is_active = FALSE WHERE id = ?", closed);
        assign(hr, org.cho.id(), "TRANSFER", closed, staff, null)
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        // 지금 직급이 비활성이어도 그대로 두고 조직만 옮기는 건 된다 — 새로 고르는 값만 활성인지 본다
        jdbc.update("UPDATE job_grade SET is_active = FALSE WHERE id = ?", staff);
        assign(hr, org.cho.id(), "TRANSFER", org.backendTeam, staff, null).andExpect(status().isCreated());
        // 사유 필수
        as(hr, post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content("""
                {"employeeId": %d, "assignmentType": "PROMOTION", "toOrgUnitId": %d, "toJobGradeId": %d}"""
                .formatted(org.yoon.id(), org.backendTeam, senior)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        // 휴직자
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", org.yoon.id());
        assign(hr, org.yoon.id(), "PROMOTION", org.backendTeam, senior, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_ACTIVE"));
        // 권한 · 다른 회사
        assign(org.seo, org.cho.id(), "PROMOTION", org.frontendTeam, senior, null).andExpect(status().isForbidden());
        TestFixture.Company other = fixture.company("다른회사");
        assign(hr, other.adminId(), "PROMOTION", org.frontendTeam, senior, null).andExpect(status().isNotFound());
    }

    @Test
    void 정정은_원본을_가리키는_새_행이고_가장_최근_유효_행으로_현재_값을_다시_계산한다() throws Exception {
        long a = assignOk(org.cho.id(), "TRANSFER", org.backendTeam, staff);
        String body = correct(a, org.devDivision, staff)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assignmentType").value("TRANSFER"))      // 원본과 같은 유형
                .andExpect(jsonPath("$.data.correctionOfId").value(a))
                .andExpect(jsonPath("$.data.correctionOf.toOrgUnitName").value("백엔드팀"))
                .andExpect(jsonPath("$.data.fromOrgUnitName").value("프론트엔드팀"))  // 변경 전 값은 원본 그대로
                .andExpect(jsonPath("$.data.toOrgUnitName").value("개발본부"))
                .andExpect(jsonPath("$.data.effectiveDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.reason").value("잘못 입력"))
                .andReturn().getResponse().getContentAsString();
        long a2 = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(current("org_unit_id", org.cho.id())).isEqualTo(org.devDivision);

        // 이미 정정된 원본은 다시 정정할 수 없다 — 가장 최근 정정본을 정정한다
        correct(a, org.frontendTeam, staff)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        // 정정본과 같은 값
        correct(a2, org.devDivision, staff).andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        // 그 뒤에 승진 발령 B — 이제 B 가 가장 최근이라 앞 건을 정정해도 현재 값은 B 그대로
        assignOk(org.cho.id(), "PROMOTION", org.devDivision, senior);
        correct(a2, org.frontendTeam, staff).andExpect(status().isCreated());
        assertThat(current("org_unit_id", org.cho.id())).isEqualTo(org.devDivision);
        assertThat(current("job_grade_id", org.cho.id())).isEqualTo(senior);

        // 목록: 정정된 행은 correctedById 로 표시, 새 행부터
        as(hr, get("/api/assignments?employeeId=" + org.cho.id()))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[3].id").value(a))
                .andExpect(jsonPath("$.data.content[3].correctedById").value(a2))
                .andExpect(jsonPath("$.data.content[0].correctionOfId").value(a2));
        // 퇴직자의 발령은 정정할 수 없다
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", org.cho.id());
        long last = jdbc.queryForObject("SELECT max(id) FROM assignment_history WHERE employee_id = ?", Long.class,
                org.cho.id());
        correct(last, org.backendTeam, staff).andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
        // 다른 회사 발령 ID
        correct(Long.MAX_VALUE, org.backendTeam, staff).andExpect(status().isNotFound());
    }

    @Test
    void 조회_범위와_필터_현재_발령_내_발령() throws Exception {
        long choMove = assignOk(org.cho.id(), "PROMOTION", org.frontendTeam, senior);
        assignOk(org.yoon.id(), "PROMOTION", org.backendTeam, senior);

        // 팀 범위(프론트엔드팀 조직장 서예린)는 자기 팀 직원 발령만
        as(org.seo, get("/api/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].employeeId").value(contains(Math.toIntExact(org.cho.id()))));
        // 전사 · 유형 필터 · 기간 필터
        as(hr, get("/api/assignments?type=PROMOTION"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        as(hr, get("/api/assignments?from=2030-03-05")).andExpect(jsonPath("$.data.totalElements").value(0));
        as(hr, get("/api/assignments?from=2030-03-05&to=2030-03-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.to").exists());

        // 현재 발령 — 하위 조직 포함, 발령이 없으면 최근 발령일 null
        as(hr, get("/api/assignments/current?orgUnitId=" + org.devDivision))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[?(@.employeeId == " + org.cho.id() + ")].jobGradeName")
                        .value(contains("대리")))
                .andExpect(jsonPath("$.data.content[?(@.employeeId == " + org.cho.id() + ")].lastAssignedDate")
                        .value(contains("2030-03-04")))
                .andExpect(jsonPath("$.data.content[?(@.employeeId == " + org.kang.id() + ")].lastAssignedDate")
                        .value(contains(nullValue())));
        as(org.seo, get("/api/assignments/current"))
                .andExpect(jsonPath("$.data.content[*].employeeId")
                        .value(containsInAnyOrder(Math.toIntExact(org.seo.id()), Math.toIntExact(org.cho.id()))));

        // 내 발령 — 권한 없이 본인 것만
        as(org.cho, get("/api/me/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.orgUnitName").value("프론트엔드팀"))
                .andExpect(jsonPath("$.data.current.jobGradeName").value("대리"))
                .andExpect(jsonPath("$.data.current.lastAssignedDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.history[*].id").value(contains(Math.toIntExact(choMove))));
        // 직원 역할(팀 범위)이지만 조직장이 아니면 범위가 비어 목록이 비어 있다
        as(org.cho, get("/api/assignments")).andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
