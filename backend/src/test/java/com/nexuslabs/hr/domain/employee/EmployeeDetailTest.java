package com.nexuslabs.hr.domain.employee;

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
import java.util.Map;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EMP-06 직원 상세 · F-EMP-03 관리자 수정 (API 설계서 6장 GET · PATCH /employees/{id}).
 * DemoOrg: 프론트엔드팀 조직장 서예린 · 팀원 조현우, 백엔드팀 윤. 시계는 2030-03-04(월) 12:00.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class EmployeeDetailTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    TestFixture.Employee hr;
    TestFixture.Employee executive;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(12, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        hr = fixture.employee(cid, org.company.rootOrgUnitId(), "인사 담당", false);
        executive = fixture.employee(cid, org.company.rootOrgUnitId(), "경영진", false);
        jdbc.update("UPDATE employee SET address = '서울시 마포구', hr_memo = '면담 예정' WHERE id = ?", org.cho.id());
        // 오늘 09:30 재택 출근 — 지각 1, 재택 1
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_in_method)
                VALUES (?, ?, ?, 'CHECKED_IN', 'REMOTE', TIMESTAMPTZ '2030-03-04 09:30+09', 'WEB')
                """, cid, org.cho.id(), Date.valueOf(MONDAY));
        // 가족 3명 — 공제 대상 배우자 · 공제 대상 자녀 · 공제 대상 아닌 부모 → 부양가족 2, 자녀 1
        jdbc.update("""
                INSERT INTO employee_family (company_id, employee_id, name, relation, is_tax_dependent)
                VALUES (?, ?, '배우자', 'SPOUSE', TRUE), (?, ?, '자녀', 'CHILD', TRUE), (?, ?, '부모', 'PARENT', FALSE)
                """, cid, org.cho.id(), cid, org.cho.id(), cid, org.cho.id());
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions detail(TestFixture.Employee viewer, long employeeId) throws Exception {
        return as(viewer, get("/api/employees/" + employeeId));
    }

    private ResultActions update(TestFixture.Employee actor, long employeeId, String body) throws Exception {
        return as(actor, patch("/api/employees/" + employeeId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 전사_범위는_전체_항목과_추가_항목_인사_메모는_EMPLOYEE_MANAGE_만() throws Exception {
        detail(hr, org.cho.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(org.cho.id()))
                .andExpect(jsonPath("$.data.orgUnitName").value("프론트엔드팀"))
                .andExpect(jsonPath("$.data.employmentTypeName").value("정규직"))
                .andExpect(jsonPath("$.data.address").value("서울시 마포구"))
                .andExpect(jsonPath("$.data.hireDate").value("2024-03-01"))
                .andExpect(jsonPath("$.data.orgUnitId").value(org.frontendTeam))
                .andExpect(jsonPath("$.data.payrollEligible").value(true))
                .andExpect(jsonPath("$.data.birthDate").isEmpty())                 // 값이 없으면 null 로 나온다
                .andExpect(jsonPath("$.data.fieldValues").isArray())
                .andExpect(jsonPath("$.data.dependentsCount").value(2))           // 가족 정보에서 센다(BR-EMP-007)
                .andExpect(jsonPath("$.data.childrenCount").value(1))
                .andExpect(jsonPath("$.data.familyMembers").doesNotExist())      // 명단은 가족 API(EMPLOYEE_MANAGE)
                .andExpect(jsonPath("$.data.hrMemo").value("면담 예정"))
                .andExpect(jsonPath("$.data.todayStatus").value("REMOTE"))
                .andExpect(jsonPath("$.data.monthSummary.month").value("2030-03"))
                .andExpect(jsonPath("$.data.monthSummary.lateCount").value(1))
                .andExpect(jsonPath("$.data.monthSummary.remoteDays").value(1))
                .andExpect(jsonPath("$.data.monthSummary.workMinutes").doesNotExist())
                .andExpect(jsonPath("$.data.bankName").doesNotExist());           // 계좌는 범위와 관계없이 없다

        // 경영진은 전사 조회만 있고 EMPLOYEE_MANAGE 가 없다 — 인사 메모 필드 자체가 빠진다
        detail(executive, org.cho.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("서울시 마포구"))
                .andExpect(jsonPath("$.data.dependentsCount").value(2))
                .andExpect(jsonPath("$.data.hrMemo").doesNotExist());
    }

    @Test
    void 팀_범위는_제한_필드만_범위_밖은_OUT_OF_SCOPE() throws Exception {
        detail(org.seo, org.cho.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeNo").exists())
                .andExpect(jsonPath("$.data.email").value(org.cho.email()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.todayStatus").value("REMOTE"))
                .andExpect(jsonPath("$.data.monthSummary.lateCount").value(1))
                .andExpect(jsonPath("$.data.address").doesNotExist())
                .andExpect(jsonPath("$.data.hireDate").doesNotExist())
                .andExpect(jsonPath("$.data.fieldValues").doesNotExist())
                .andExpect(jsonPath("$.data.dependentsCount").doesNotExist())
                .andExpect(jsonPath("$.data.childrenCount").doesNotExist())
                .andExpect(jsonPath("$.data.hrMemo").doesNotExist());

        detail(org.seo, org.yoon.id())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
        TestFixture.Company other = fixture.company("다른회사");
        detail(hr, other.adminId()).andExpect(status().isNotFound());
    }

    @Test
    void 퇴직자도_상세는_보이고_오늘_상태는_null() throws Exception {
        as(hr, post("/api/employees/" + org.cho.id() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "RESIGNED", "effectiveDate": "2030-03-04", "reason": "개인 사정"}"""))
                .andExpect(status().isOk());
        detail(hr, org.cho.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESIGNED"))
                .andExpect(jsonPath("$.data.todayStatus").isEmpty());
    }

    @Test
    void 관리자_수정은_보낸_항목만_바꾸고_조직_직급_직책_사원번호는_무시한다() throws Exception {
        long contract = jdbc.queryForObject("""
                INSERT INTO employment_type (company_id, name, sort_order) VALUES (?, '계약직2', 9) RETURNING id
                """, Long.class, cid);
        String employeeNo = jdbc.queryForObject("SELECT employee_no FROM employee WHERE id = ?", String.class,
                org.cho.id());
        String newEmail = "renamed" + org.cho.id() + "@test.example";       // 테스트 데이터를 지우지 않아 실행마다 다르게

        update(hr, org.cho.id(), """
                {"name": " 조현우 ", "email": "%s", "employmentTypeId": %d,
                 "payrollEligible": false, "birthDate": "1995-05-05", "gender": "MALE", "address": null,
                 "contractEndDate": "2031-03-03", "hrMemo": "재계약 검토",
                 "orgUnitId": %d, "jobTitleId": %d, "employeeNo": "X-1"}""".formatted(newEmail.toUpperCase(), contract,
                        org.backendTeam, org.ceoTitle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("조현우"))
                .andExpect(jsonPath("$.data.email").value(newEmail))
                .andExpect(jsonPath("$.data.employmentTypeName").value("계약직2"))
                .andExpect(jsonPath("$.data.address").isEmpty())
                .andExpect(jsonPath("$.data.hrMemo").value("재계약 검토"))
                .andExpect(jsonPath("$.data.monthSummary").exists());              // 응답 = GET 과 같은 모양

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT name, email, employment_type_id, payroll_eligible, birth_date::text AS birth_date,
                       gender::text AS gender, address, contract_end_date::text AS contract_end_date, phone,
                       org_unit_id, job_title_id, employee_no
                FROM employee WHERE id = ?""", org.cho.id());
        assertThat(row.get("name")).isEqualTo("조현우");
        assertThat(row.get("email")).isEqualTo(newEmail);
        assertThat(((Number) row.get("employment_type_id")).longValue()).isEqualTo(contract);
        assertThat(row.get("payroll_eligible")).isEqualTo(false);
        assertThat(row.get("birth_date")).isEqualTo("1995-05-05");
        assertThat(row.get("gender")).isEqualTo("MALE");
        assertThat(row.get("address")).isNull();
        assertThat(row.get("contract_end_date")).isEqualTo("2031-03-03");
        // 발령으로만 바뀌는 항목(BR-EMP-001)과 사원번호(BR-EMP-006)는 그대로
        assertThat(((Number) row.get("org_unit_id")).longValue()).isEqualTo(org.frontendTeam);
        assertThat(row.get("job_title_id")).isNull();
        assertThat(row.get("employee_no")).isEqualTo(employeeNo);

        // 바뀐 이메일로 로그인 ID 가 바뀐다 — 이전 이메일은 다른 직원이 쓸 수 있다
        update(hr, org.yoon.id(), """
                {"email": "%s"}""".formatted(org.cho.email())).andExpect(status().isOk());
    }

    @Test
    void 수정_거부_규칙() throws Exception {
        // 다른 직원의 이메일
        update(hr, org.cho.id(), """
                {"email": "%s"}""".formatted(org.seo.email().toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATE"));
        // 본인 이메일 그대로는 된다
        update(hr, org.cho.id(), """
                {"email": "%s"}""".formatted(org.cho.email())).andExpect(status().isOk());
        // 모르는 항목 · 비울 수 없는 항목 · 형식
        update(hr, org.cho.id(), """
                {"nickname": "조조", "name": null, "email": "not-an-email"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.nickname").exists())
                .andExpect(jsonPath("$.error.fields.name").exists());
        update(hr, org.cho.id(), """
                {"email": "not-an-email"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.email").exists());
        // 비활성 고용형태로 바꾸기
        long inactive = jdbc.queryForObject("""
                INSERT INTO employment_type (company_id, name, sort_order, is_active) VALUES (?, '폐지', 9, FALSE)
                RETURNING id""", Long.class, cid);
        update(hr, org.cho.id(), """
                {"employmentTypeId": %d}""".formatted(inactive))
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        // 최고 관리자는 ROLE_MANAGE 가 있어야 고친다 — 인사 담당은 못 하고 최고 관리자 본인은 된다(2026-10-10)
        TestFixture.Employee admin = new TestFixture.Employee(org.company.adminId(), cid, org.company.adminEmail());
        update(hr, admin.id(), """
                {"phone": "010-9999-9999"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        update(admin, admin.id(), """
                {"phone": "010-9999-9999"}""").andExpect(status().isOk());
        // 권한 · 다른 회사
        update(org.seo, org.cho.id(), """
                {"phone": "010-0000-0000"}""").andExpect(status().isForbidden());
        TestFixture.Company other = fixture.company("다른회사");
        update(hr, other.adminId(), """
                {"phone": "010-0000-0000"}""").andExpect(status().isNotFound());
        // 퇴직자
        as(hr, post("/api/employees/" + org.cho.id() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "RESIGNED", "effectiveDate": "2030-03-04", "reason": "개인 사정"}"""))
                .andExpect(status().isOk());
        update(hr, org.cho.id(), """
                {"phone": "010-0000-0000"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
    }
}
