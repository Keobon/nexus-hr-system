package com.nexuslabs.hr.domain.company;

import com.nexuslabs.hr.global.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-COMP-01 회사 등록. 기본값 구성은 기능명세서 부록 B · ERD 8장과 같아야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompanyRegistrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private static String body(String regNo, String adminEmail, String password) {
        return """
                {"company": {"name": "테스트랩스", "businessRegNo": "%s", "ceoName": "박대표",
                             "address": "서울특별시 강남구", "phone": "02-555-0100", "email": "contact@test.example"},
                 "admin": {"name": "이관리", "email": "%s", "password": "%s"}}
                """.formatted(regNo, adminEmail, password);
    }

    private ResultActions register(String regNo, String adminEmail, String password) throws Exception {
        return mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                .content(body(regNo, adminEmail, password)));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void 등록하면_기본값이_복사되고_관리자로_바로_쓸_수_있다() throws Exception {
        String json = register("900-11-00001", "boss@test.example", "Passw0rd!")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.next").value("SETUP_WIZARD"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(json).get("data");
        long cid = data.get("companyId").asLong();
        long eid = data.get("employeeId").asLong();
        LocalDate today = LocalDate.now(ClockConfig.ZONE);

        // 역할 4개와 권한 수(부록 B)
        Map<String, Object> perms = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE r.name = '최고 관리자') AS super_admin,
                       count(*) FILTER (WHERE r.name = '인사 담당') AS hr,
                       count(*) FILTER (WHERE r.name = '경영진') AS exec,
                       count(*) FILTER (WHERE r.name = '직원' AND p.scope = 'TEAM') AS emp
                FROM role_permission p JOIN role r ON r.id = p.role_id WHERE p.company_id = ?
                """, cid);
        assertThat(perms).containsEntry("super_admin", 18L).containsEntry("hr", 14L)
                .containsEntry("exec", 7L).containsEntry("emp", 5L);
        assertThat(count("SELECT count(*) FROM role WHERE company_id = ?", cid)).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM role WHERE company_id = ? AND is_system", cid)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT name FROM org_unit WHERE company_id = ? AND parent_id IS NULL AND lead_employee_id IS NULL",
                String.class, cid)).isEqualTo("테스트랩스");
        assertThat(count("SELECT count(*) FROM org_unit WHERE company_id = ?", cid)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM employment_type WHERE company_id = ?", cid)).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM work_schedule WHERE company_id = ? AND effective_from = ? AND work_days = 31",
                cid, today)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM leave_type WHERE company_id = ?", cid)).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM expense_type WHERE company_id = ? AND receipt_required", cid)).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM expense_type WHERE company_id = ?", cid)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM approval_line WHERE company_id = ? AND is_default", cid)).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM approval_line_step WHERE company_id = ? AND step_order = 1 AND approver_type = 'ORG_LEAD'",
                cid)).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM pay_item WHERE company_id = ? AND calc_method = 'TAXABLE_RATE'", cid)).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM pay_item WHERE company_id = ? AND calc_method = 'ATTENDANCE'", cid)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM pay_item WHERE company_id = ? AND calc_method = 'TRIP_EXPENSE' AND NOT is_taxable", cid)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pay_variable WHERE company_id = ? AND effective_from = ?", cid, today)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM tax_bracket WHERE company_id = ?", cid)).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT name FROM job_grade WHERE company_id = ? ORDER BY sort_order", String.class, cid))
                .containsExactly("사원", "대리", "과장", "차장", "부장");
        assertThat(count("SELECT count(*) FROM holiday WHERE company_id = ?", cid)).isZero();

        // 관리자: 최상위 조직 · 정규직 · 직급 없음 · 사원번호 자동 · 입사 이력 · 비밀번호 변경 불필요
        Map<String, Object> admin = jdbc.queryForMap("""
                SELECT e.employee_no, e.hire_date, e.job_grade_id, e.status::text AS status, t.name AS type_name,
                       o.parent_id, r.name AS role_name, a.must_change_password
                FROM employee e JOIN employment_type t ON t.id = e.employment_type_id
                JOIN org_unit o ON o.id = e.org_unit_id
                JOIN account a ON a.employee_id = e.id JOIN role r ON r.id = a.role_id
                WHERE e.id = ? AND e.company_id = ?
                """, eid, cid);
        assertThat(admin).containsEntry("employee_no", today.getYear() + "-0001")
                .containsEntry("type_name", "정규직").containsEntry("status", "ACTIVE")
                .containsEntry("role_name", "최고 관리자").containsEntry("must_change_password", false)
                .containsEntry("job_grade_id", null).containsEntry("parent_id", null);
        assertThat(count("SELECT count(*) FROM employment_status_history WHERE employee_id = ? AND status = 'ACTIVE'", eid))
                .isEqualTo(1);

        // 받은 토큰으로 바로 로그인 상태
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + data.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.setupCompleted").value(false))
                .andExpect(jsonPath("$.data.role.name").value("최고 관리자"))
                .andExpect(jsonPath("$.data.permissions.length()").value(18));
    }

    @Test
    void 사업자등록번호는_숫자만_보내도_형식을_맞춰_저장한다() throws Exception {
        long cid = objectMapper.readTree(register("9001100002", "b2@test.example", "Passw0rd!")
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("data").get("companyId").asLong();
        assertThat(jdbc.queryForObject("SELECT business_reg_no FROM company WHERE id = ?", String.class, cid))
                .isEqualTo("900-11-00002");
    }

    @Test
    void 이미_등록된_사업자등록번호나_이메일은_거부한다() throws Exception {
        register("900-11-00003", "dup@test.example", "Passw0rd!").andExpect(status().isCreated());

        register("9001100003", "other@test.example", "Passw0rd!")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_REG_NO_DUPLICATE"));
        // 이메일은 대소문자를 구분하지 않는다
        register("900-11-00004", "DUP@Test.example", "Passw0rd!")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATE"));
    }

    @Test
    void 입력_규칙_위반은_필드별로_알려준다() throws Exception {
        register("12345", "bad@test.example", "short1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields['company.businessRegNo']").exists())
                .andExpect(jsonPath("$.error.fields['admin.password']").value("8자 이상, 영문과 숫자를 포함해야 합니다"));
        register("900-11-00005", "bad@test.example", "onlyletters")
                .andExpect(jsonPath("$.error.fields['admin.password']").exists());
    }

    @Test
    void 사원번호는_회사의_접두어와_입사연도로_이어서_매긴다() throws Exception {
        long cid = objectMapper.readTree(register("900-11-00006", "seq@test.example", "Passw0rd!")
                        .andReturn().getResponse().getContentAsString())
                .get("data").get("companyId").asLong();
        jdbc.update("UPDATE company SET employee_no_prefix = 'TL-' WHERE id = ?", cid);
        var generator = new com.nexuslabs.hr.domain.employee.service.EmployeeNoGenerator(jdbc);
        int year = LocalDate.now(ClockConfig.ZONE).getYear();
        // 직접 입력한 번호와 겹치면 건너뛴다
        long root = jdbc.queryForObject("SELECT id FROM org_unit WHERE company_id = ?", Long.class, cid);
        long type = jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '정규직'", Long.class, cid);
        jdbc.update("""
                INSERT INTO employee (company_id, employee_no, name, email, hire_date, org_unit_id, employment_type_id)
                VALUES (?, ?, '수동', 'manual@test.example', CURRENT_DATE, ?, ?)
                """, cid, "TL-" + year + "-0002", root, type);
        assertThat(generator.next(cid, year)).isEqualTo("TL-" + year + "-0003");
        assertThat(generator.next(cid, 2025)).isEqualTo("TL-2025-0001");
    }
}
