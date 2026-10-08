package com.nexuslabs.hr.domain.employee;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EMP-04 재직상태 관리 (API 설계서 6장) — 퇴직 연쇄 처리(역할 분담 v2 2.3 9번)를 실제 신청 · 승인 엔진과 함께 확인한다.
 * DemoOrg: 프론트엔드팀 조직장 서예린 · 팀원 조현우, 개발본부장 강하늘. 시계는 2030-03-04(월) 09:00, 픽스처 입사일은 2024-03-01.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class EmploymentStatusTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    TestFixture.Employee hr;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        hr = fixture.employee(cid, org.company.rootOrgUnitId(), "인사 담당", false);
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, cid)));
    }

    private ResultActions changeStatus(long actorId, long employeeId, String status, String effectiveDate)
            throws Exception {
        return as(actorId, post("/api/employees/" + employeeId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s", "effectiveDate": "%s", "reason": "개인 사정"}
                        """.formatted(status, effectiveDate)));
    }

    private long create(TestFixture.Employee e, String path, String body) throws Exception {
        String response = as(e.id(), post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private String statusOf(String table, long id) {
        return jdbc.queryForObject("SELECT status::text FROM " + table + " WHERE id = ?", String.class, id);
    }

    @Test
    void 퇴직하면_계정_조직장_대기_신청_승인_단계까지_한_번에_처리된다() throws Exception {
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, cid);
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2030, 'REGULAR', 15)""", cid, org.seo.id(), annual);
        // 서예린 본인의 승인대기 신청 3건
        long leave = create(org.seo, "/api/me/leave-requests", """
                {"leaveTypeId": %d, "startDate": "2030-03-11", "endDate": "2030-03-11"}""".formatted(annual));
        long overtime = create(org.seo, "/api/me/overtime-requests", """
                {"workDate": "2030-03-05", "plannedStart": "18:00", "plannedEnd": "21:00", "reason": "배포"}""");
        long trip = create(org.seo, "/api/me/business-trips", """
                {"tripType": "DOMESTIC", "destination": "부산", "purpose": "고객사 방문",
                 "startDate": "2030-03-13", "endDate": "2030-03-14"}""");
        // 서예린이 1단계 승인자인 조현우의 출장
        long choTrip = create(org.cho, "/api/me/business-trips", """
                {"tripType": "DOMESTIC", "destination": "대구", "purpose": "교육",
                 "startDate": "2030-03-13", "endDate": "2030-03-13"}""");

        // 마지막 근무일 3/3(일) → 발효일 3/4
        changeStatus(hr.id(), org.seo.id(), "RESIGNED", "2030-03-04")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESIGNED"))
                .andExpect(jsonPath("$.data.effectiveDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.reason").value("개인 사정"))
                .andExpect(jsonPath("$.data.createdById").value(hr.id()));

        // ① 상태
        assertThat(statusOf("employee", org.seo.id())).isEqualTo("RESIGNED");
        // ② 계정 비활성 + 감사 로그 → 그 토큰으로는 아무것도 못 한다
        assertThat(jdbc.queryForObject("SELECT is_active FROM account WHERE employee_id = ?", Boolean.class,
                org.seo.id())).isFalse();
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'ACCOUNT' AND target_id = ?
                        """, Long.class, cid, org.seo.id())).isEqualTo(1);
        as(org.seo.id(), get("/api/me")).andExpect(status().isUnauthorized());
        // ③ 조직장 해제
        assertThat(jdbc.queryForObject("SELECT lead_employee_id FROM org_unit WHERE id = ?", Long.class,
                org.frontendTeam)).isNull();
        // ④ 본인 승인대기 신청 자동 취소(사유 "퇴직") + 단계 취소
        assertThat(statusOf("leave_request", leave)).isEqualTo("CANCELLED");
        assertThat(statusOf("overtime_request", overtime)).isEqualTo("CANCELLED");
        assertThat(statusOf("business_trip", trip)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM leave_request WHERE id = ?", String.class, leave))
                .isEqualTo("퇴직");
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM approval_step WHERE company_id = ? AND target_id IN (?, ?, ?)
                          AND status IN ('PENDING', 'WAITING')
                        """, Long.class, cid, leave, overtime, trip)).isZero();
        // ⑤ 남의 신청에서 서예린이 승인자인 대기 단계는 재지정 필요
        assertThat(statusOf("business_trip", choTrip)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                        SELECT needs_reassign FROM approval_step
                        WHERE company_id = ? AND work_type = 'BUSINESS_TRIP' AND target_id = ? AND status = 'PENDING'
                        """, Boolean.class, cid, choTrip)).isTrue();

        // 이력은 시간순, 퇴직자는 다시 바꿀 수 없다
        as(hr.id(), get("/api/employees/" + org.seo.id() + "/status-history"))
                .andExpect(jsonPath("$.data[*].status").value(contains("RESIGNED")));
        changeStatus(hr.id(), org.seo.id(), "ACTIVE", "2030-03-04")
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
    }

    @Test
    void 휴직과_복직은_이력만_남기고_연쇄_처리는_없다() throws Exception {
        changeStatus(hr.id(), org.seo.id(), "ON_LEAVE", "2030-03-01").andExpect(status().isOk());
        changeStatus(hr.id(), org.seo.id(), "ON_LEAVE", "2030-03-02")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        changeStatus(hr.id(), org.seo.id(), "ACTIVE", "2030-03-04").andExpect(status().isOk());

        assertThat(statusOf("employee", org.seo.id())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT is_active FROM account WHERE employee_id = ?", Boolean.class,
                org.seo.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT lead_employee_id FROM org_unit WHERE id = ?", Long.class,
                org.frontendTeam)).isEqualTo(org.seo.id());
        as(hr.id(), get("/api/employees/" + org.seo.id() + "/status-history"))
                .andExpect(jsonPath("$.data[*].status").value(contains("ON_LEAVE", "ACTIVE")))
                .andExpect(jsonPath("$.data[0].effectiveDate").value("2030-03-01"));
    }

    @Test
    void 발효일은_오늘까지_입사일과_마지막_변경일_이후() throws Exception {
        changeStatus(hr.id(), org.cho.id(), "ON_LEAVE", "2030-03-05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.effectiveDate").exists());
        changeStatus(hr.id(), org.cho.id(), "ON_LEAVE", "2024-02-29")
                .andExpect(jsonPath("$.error.fields.effectiveDate").exists());
        changeStatus(hr.id(), org.cho.id(), "ON_LEAVE", "2030-03-02").andExpect(status().isOk());
        changeStatus(hr.id(), org.cho.id(), "ACTIVE", "2030-03-01")
                .andExpect(jsonPath("$.error.fields.effectiveDate").exists());
        // 마지막 변경일과 같은 날은 된다
        changeStatus(hr.id(), org.cho.id(), "ACTIVE", "2030-03-02").andExpect(status().isOk());
    }

    @Test
    void 마지막_최고_관리자는_퇴직할_수_없고_아무것도_바뀌지_않는다() throws Exception {
        long admin = org.company.adminId();
        changeStatus(hr.id(), admin, "RESIGNED", "2030-03-04")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_SUPER_ADMIN"));
        assertThat(statusOf("employee", admin)).isEqualTo("ACTIVE");
        // 회사 등록 때 생긴 입사 이력만 남는다
        assertThat(jdbc.queryForList("SELECT status::text FROM employment_status_history WHERE employee_id = ?",
                String.class, admin)).containsExactly("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT is_active FROM account WHERE employee_id = ?", Boolean.class, admin))
                .isTrue();
    }

    @Test
    void 권한과_범위() throws Exception {
        // 직원 역할은 EMPLOYEE_MANAGE 가 없다
        changeStatus(org.cho.id(), org.yoon.id(), "ON_LEAVE", "2030-03-04").andExpect(status().isForbidden());
        // 조직장(EMPLOYEE_READ 팀 범위)은 이력을 못 본다 — 전사 범위만
        as(org.seo.id(), get("/api/employees/" + org.cho.id() + "/status-history"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
        // 다른 회사 직원은 없는 것과 같다
        TestFixture.Company other = fixture.company("다른회사");
        changeStatus(hr.id(), other.adminId(), "ON_LEAVE", "2030-03-04").andExpect(status().isNotFound());
    }
}
