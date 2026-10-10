package com.nexuslabs.hr.domain.employee;

import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EMP-01 직원 등록 (API 설계서 6장). 계정 생성(F-AUTH-04)과 입사 휴가 부여(F-LEAVE-02)까지 한 트랜잭션이다.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeRegistrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    long gradeId;
    long regularTypeId;

    @BeforeEach
    void setUp() {
        company = fixture.company("직원등록테스트");
        gradeId = jdbc.queryForObject("SELECT id FROM job_grade WHERE company_id = ? AND name = '사원'", Long.class,
                company.id());
        regularTypeId = jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '정규직'",
                Long.class, company.id());
    }

    private long insertItem(String table, String name) {
        return jdbc.queryForObject("INSERT INTO " + table + " (company_id, name, sort_order) VALUES (?, ?, 1) RETURNING id",
                Long.class, company.id(), name);
    }

    private String email(String local) {
        return local + "." + company.id() + "@reg.example";
    }

    /** 필수 값만 채운 등록 요청에 extra 를 덧붙인다(같은 키는 extra 가 이긴다 — JSON 은 마지막 값이 쓰인다). */
    private String body(String emailLocal, String extra) {
        return """
                {"name": "오지민", "email": "%s", "hireDate": "2026-03-03", "orgUnitId": %d, "jobGradeId": %d,
                 "employmentTypeId": %d%s}
                """.formatted(email(emailLocal), company.rootOrgUnitId(), gradeId, regularTypeId,
                extra.isEmpty() ? "" : ", " + extra);
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(json)
                .header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private Map<String, Object> employeeRow(String emailLocal) {
        return jdbc.queryForMap("SELECT * FROM employee WHERE email = ?", email(emailLocal));
    }

    private boolean employeeExists(String emailLocal) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee WHERE email = ?)", Boolean.class,
                email(emailLocal));
    }

    // ---------------------------------------------------------------- 등록

    @Test
    void 등록하면_사원번호_재직이력_계정_입사휴가가_함께_만들어진다() throws Exception {
        register(body("jimin", "\"employeeNo\": null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.employeeNo").value(matchesPattern("^2026-\\d{4}$")))
                .andExpect(jsonPath("$.data.name").value("오지민"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.temporaryPassword").value(matchesPattern("^.{10}$")))
                // 3월 입사: 연차 15일 × 남은 10개월 ÷ 12 = 12일(내림). 병가·경조사는 0일이라 부여하지 않는다
                .andExpect(jsonPath("$.data.leaveGrants").value(hasSize(1)))
                .andExpect(jsonPath("$.data.leaveGrants[0].leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.leaveGrants[0].leaveYear").value(2026))
                .andExpect(jsonPath("$.data.leaveGrants[0].grantType").value("HIRE"))
                .andExpect(jsonPath("$.data.leaveGrants[0].days").value(12));

        Map<String, Object> employee = employeeRow("jimin");
        long employeeId = (Long) employee.get("id");
        assertThat(employee.get("company_id")).isEqualTo(company.id());
        assertThat(employee.get("org_unit_id")).isEqualTo(company.rootOrgUnitId());
        assertThat(employee.get("job_grade_id")).isEqualTo(gradeId);
        assertThat(employee.get("payroll_eligible")).isEqualTo(true);

        Map<String, Object> history = jdbc.queryForMap(
                "SELECT status::text AS status, effective_date, reason, created_by FROM employment_status_history "
                        + "WHERE employee_id = ?", employeeId);
        assertThat(history.get("status")).isEqualTo("ACTIVE");
        assertThat(history.get("effective_date").toString()).isEqualTo("2026-03-03");
        assertThat(history.get("reason")).isEqualTo("입사");
        assertThat(history.get("created_by")).isEqualTo(company.adminId());

        Map<String, Object> account = jdbc.queryForMap("""
                SELECT a.must_change_password, a.is_active, r.name AS role_name
                FROM account a JOIN role r ON r.id = a.role_id WHERE a.employee_id = ?
                """, employeeId);
        assertThat(account.get("must_change_password")).isEqualTo(true);
        assertThat(account.get("is_active")).isEqualTo(true);
        assertThat(account.get("role_name")).isEqualTo("직원");
    }

    @Test
    void 임시_비밀번호로_로그인하면_비밀번호_변경으로_안내된다() throws Exception {
        String response = register(body("login", "")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = response.replaceAll(".*\"temporaryPassword\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email("login"), temporaryPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.next").value("CHANGE_PASSWORD"));
    }

    @Test
    void 선택_항목과_역할을_지정해_등록한다() throws Exception {
        long titleId = insertItem("job_title", "파트장");
        long hrRoleId = jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = '인사 담당'",
                Long.class, company.id());

        register(body("full", """
                "employeeNo": " EMP-77 ", "email": "Full.%d@REG.example", "phone": "010-1000-0009", "address": "서울",
                "jobTitleId": %d, "roleId": %d, "payrollEligible": false, "nameEn": "Oh Jimin",
                "birthDate": "1999-12-01", "gender": "FEMALE", "emergencyName": "오부모", "emergencyRelation": "부",
                "emergencyPhone": "010-2222-3333", "contractEndDate": "2027-03-02", "probationEndDate": "2026-06-02",
                "hrMemo": "경력 3년"
                """.formatted(company.id(), titleId, hrRoleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeNo").value("EMP-77"));

        // 이메일은 소문자로 저장한다(로그인 ID)
        Map<String, Object> employee = employeeRow("full");
        assertThat(employee.get("employee_no")).isEqualTo("EMP-77");
        assertThat(employee.get("phone")).isEqualTo("010-1000-0009");
        assertThat(employee.get("job_title_id")).isEqualTo(titleId);
        assertThat(employee.get("payroll_eligible")).isEqualTo(false);
        assertThat(employee.get("name_en")).isEqualTo("Oh Jimin");
        assertThat(employee.get("birth_date").toString()).isEqualTo("1999-12-01");
        assertThat(String.valueOf(employee.get("gender"))).isEqualTo("FEMALE");
        assertThat(employee.get("emergency_name")).isEqualTo("오부모");
        assertThat(employee.get("contract_end_date").toString()).isEqualTo("2027-03-02");
        assertThat(employee.get("hr_memo")).isEqualTo("경력 3년");
        assertThat(jdbc.queryForObject("SELECT role_id FROM account WHERE employee_id = ?", Long.class,
                employee.get("id"))).isEqualTo(hrRoleId);
    }

    // ---------------------------------------------------------------- 중복

    @Test
    void 사원번호는_회사_안에서만_겹치면_안_된다() throws Exception {
        register(body("first", "\"employeeNo\": \"A-001\"")).andExpect(status().isCreated());

        register(body("second", "\"employeeNo\": \"A-001\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NO_DUPLICATE"));
        assertThat(employeeExists("second")).isFalse();

        // 다른 회사는 같은 사원번호를 쓸 수 있다
        TestFixture.Company other = fixture.company("직원등록테스트타사");
        long otherGrade = jdbc.queryForObject(
                "SELECT id FROM job_grade WHERE company_id = ? AND name = '사원'", Long.class,
                other.id());
        long otherType = jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '정규직'",
                Long.class, other.id());
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("""
                        {"employeeNo": "A-001", "name": "타사직원", "email": "%s", "hireDate": "2026-03-03",
                         "orgUnitId": %d, "jobGradeId": %d, "employmentTypeId": %d}
                        """.formatted(email("other"), other.rootOrgUnitId(), otherGrade, otherType))
                        .header("Authorization", fixture.token(other.adminId(), other.id())))
                .andExpect(status().isCreated());
    }

    @Test
    void 이메일은_다른_회사와도_겹치면_안_된다() throws Exception {
        register(body("dup", "")).andExpect(status().isCreated());

        // 같은 회사, 대소문자만 다른 이메일
        register(body("dup", "\"email\": \"%s\"".formatted(email("DUP"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATE"));
        // 다른 회사에 이미 있는 이메일(다른 회사의 관리자)
        TestFixture.Company other = fixture.company("직원등록테스트타사");
        register(body("x", "\"email\": \"%s\"".formatted(other.adminEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATE"));
    }

    // ---------------------------------------------------------------- 거부

    @Test
    void 비활성이거나_없는_조직_직급_직책_고용형태는_거부한다() throws Exception {
        long closedOrg = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "폐지팀");
        jdbc.update("UPDATE org_unit SET is_active = FALSE WHERE id = ?", closedOrg);
        long closedGrade = insertItem("job_grade", "폐지직급");
        jdbc.update("UPDATE job_grade SET is_active = FALSE WHERE id = ?", closedGrade);
        long closedTitle = insertItem("job_title", "폐지직책");
        jdbc.update("UPDATE job_title SET is_active = FALSE WHERE id = ?", closedTitle);
        TestFixture.Company other = fixture.company("직원등록테스트타사");

        register(body("a", "\"orgUnitId\": " + closedOrg))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        register(body("b", "\"jobGradeId\": " + closedGrade))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        register(body("c", "\"jobTitleId\": " + closedTitle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        register(body("d", "\"employmentTypeId\": 999999999")).andExpect(status().isNotFound());
        // 다른 회사의 조직은 없는 것으로 본다
        register(body("e", "\"orgUnitId\": " + other.rootOrgUnitId())).andExpect(status().isNotFound());
        assertThat(employeeExists("a") || employeeExists("b") || employeeExists("c") || employeeExists("d")
                || employeeExists("e")).isFalse();
    }

    @Test
    void 입사일이_미래이거나_필수값이_없으면_입력값_오류() throws Exception {
        LocalDate tomorrow = LocalDate.now(ClockConfig.ZONE).plusDays(1);
        register(body("future", "\"hireDate\": \"" + tomorrow + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.hireDate").exists());
        // 오늘 입사는 된다
        register(body("today", "\"hireDate\": \"" + tomorrow.minusDays(1) + "\"")).andExpect(status().isCreated());

        register("{\"name\": \" \", \"email\": \"메일아님\", \"orgUnitId\": %d}".formatted(company.rootOrgUnitId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.hireDate").exists())
                .andExpect(jsonPath("$.error.fields.jobGradeId").exists())
                .andExpect(jsonPath("$.error.fields.employmentTypeId").exists());
        register(body("gender", "\"gender\": \"OTHER\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
    }

    @Test
    void 중간에_실패하면_직원도_사원번호_순번도_남지_않는다() throws Exception {
        Integer before = jdbc.queryForObject(
                "SELECT last_seq FROM employee_no_seq WHERE company_id = ? AND hire_year = 2026", Integer.class,
                company.id());

        // 직원 저장 뒤 계정을 만들 때 없는 역할이라 실패한다
        register(body("rollback", "\"roleId\": 999999999")).andExpect(status().isNotFound());

        assertThat(employeeExists("rollback")).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT last_seq FROM employee_no_seq WHERE company_id = ? AND hire_year = 2026", Integer.class,
                company.id())).isEqualTo(before);
    }

    // ---------------------------------------------------------------- 추가 항목

    @Test
    void 추가_항목_값은_타입대로_검증해_저장한다() throws Exception {
        long education = jdbc.queryForObject("""
                INSERT INTO employee_field_def (company_id, name, field_type, is_required, is_multiple, sort_order)
                VALUES (?, '학력', 'TEXT', TRUE, TRUE, 1) RETURNING id
                """, Long.class, company.id());
        long skill = jdbc.queryForObject("""
                INSERT INTO employee_field_def (company_id, name, field_type, options, sort_order)
                VALUES (?, '기술 스택', 'SELECT', '["Java", "React"]'::jsonb, 2) RETURNING id
                """, Long.class, company.id());
        long certDate = jdbc.queryForObject("""
                INSERT INTO employee_field_def (company_id, name, field_type, sort_order)
                VALUES (?, '보건증 만료일', 'DATE', 3) RETURNING id
                """, Long.class, company.id());

        // 필수 항목(학력) 누락
        register(body("f1", "\"fieldValues\": []"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields['fieldValues." + education + "']").exists());
        // 선택지에 없는 값, 날짜가 아닌 값, 한 건만 되는 항목에 두 건
        register(body("f2", """
                "fieldValues": [{"fieldDefId": %d, "value": "OO대 졸업"}, {"fieldDefId": %d, "value": "Go"},
                                {"fieldDefId": %d, "value": "내년"}]
                """.formatted(education, skill, certDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['fieldValues." + skill + "']").exists())
                .andExpect(jsonPath("$.error.fields['fieldValues." + certDate + "']").exists());
        register(body("f3", """
                "fieldValues": [{"fieldDefId": %d, "value": "OO대 졸업"}, {"fieldDefId": %d, "value": "Java"},
                                {"fieldDefId": %d, "value": "React"}]
                """.formatted(education, skill, skill)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['fieldValues." + skill + "']").exists());
        assertThat(employeeExists("f1") || employeeExists("f2") || employeeExists("f3")).isFalse();

        register(body("f4", """
                "fieldValues": [{"fieldDefId": %d, "value": "OO고 졸업"}, {"fieldDefId": %d, "value": " OO대 졸업 "},
                                {"fieldDefId": %d, "value": "React"}, {"fieldDefId": %d, "value": "2027-01-31"}]
                """.formatted(education, education, skill, certDate)))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForList("""
                SELECT v.seq || ':' || v.value FROM employee_field_value v JOIN employee e ON e.id = v.employee_id
                WHERE e.email = ? ORDER BY v.field_def_id, v.seq
                """, String.class, email("f4")))
                .containsExactly("1:OO고 졸업", "2:OO대 졸업", "1:React", "1:2027-01-31");
    }

    // ---------------------------------------------------------------- 권한

    @Test
    void EMPLOYEE_MANAGE_가_없으면_등록할_수_없다() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(body("nope", ""))
                        .header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(employeeExists("nope")).isFalse();
    }
}
