package com.nexuslabs.hr.domain.employee;

import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Date;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EMP-02 직원 목록 · F-EMP-05 개인 페이지 (API 설계서 6장).
 * JPA 를 쓰는 기능과 같은 방식으로 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeListTest {

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

    /** 회사(관리자) — 개발본부(본부장) — 백엔드팀(개발자) / 사업본부(영업). */
    @BeforeEach
    void setUp() {
        company = fixture.company("직원목록테스트");
        dev = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "개발본부");
        team = fixture.orgUnit(company.id(), dev, "백엔드팀");
        biz = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "사업본부");
        devLead = member(dev, "강하늘");
        developer = member(team, "임태현");
        seller = member(biz, "신유나");
        fixture.lead(company.id(), dev, devLead);
    }

    private long member(long orgUnitId, String name) {
        long id = fixture.employee(company.id(), orgUnitId, "직원", false).id();
        jdbc.update("UPDATE employee SET name = ? WHERE id = ?", name, id);
        return id;
    }

    private ResultActions asAdmin(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private ResultActions as(long employeeId, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", fixture.token(employeeId, company.id())));
    }

    private void attendance(long employeeId, String status, String workType, boolean checkedOut) {
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at)
                VALUES (?, ?, ?, ?::attendance_status, ?::work_type, now() - interval '2 hours',
                        CASE WHEN ? THEN now() - interval '1 hour' END)
                """, company.id(), employeeId, Date.valueOf(LocalDate.now(ClockConfig.ZONE)), status, workType, checkedOut);
    }

    // ---------------------------------------------------------------- 목록

    @Test
    void 목록은_사원번호_순으로_소속과_오늘_상태를_담는다() throws Exception {
        long gradeId = jdbc.queryForObject(
                "SELECT id FROM job_grade WHERE company_id = ? AND name = '대리'", Long.class,
                company.id());
        jdbc.update("UPDATE employee SET job_grade_id = ? WHERE id = ?", gradeId, developer);

        asAdmin("/api/employees")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[*].name").value(contains("강하늘", "임태현", "신유나", "관리자")))
                .andExpect(jsonPath("$.data.content[1].id").value(developer))
                .andExpect(jsonPath("$.data.content[1].employeeNo").exists())
                .andExpect(jsonPath("$.data.content[1].orgUnitId").value(team))
                .andExpect(jsonPath("$.data.content[1].orgUnitName").value("백엔드팀"))
                .andExpect(jsonPath("$.data.content[1].jobGradeName").value("대리"))
                .andExpect(jsonPath("$.data.content[1].jobTitleName").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].employmentTypeName").value("정규직"))
                .andExpect(jsonPath("$.data.content[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[1].todayStatus").value("BEFORE_WORK"))
                .andExpect(jsonPath("$.data.content[1].hireDate").value("2024-03-01"));

        asAdmin("/api/employees?size=2&page=1")
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[*].name").value(contains("신유나", "관리자")));
    }

    @Test
    void 조직_필터는_하위_조직까지_포함하고_다른_필터와_함께_쓸_수_있다() throws Exception {
        long contract = jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '계약직'",
                Long.class, company.id());
        jdbc.update("UPDATE employee SET employment_type_id = ? WHERE id = ?", contract, developer);

        asAdmin("/api/employees?orgUnitId=" + dev)
                .andExpect(jsonPath("$.data.content[*].name").value(contains("강하늘", "임태현")));
        asAdmin("/api/employees?orgUnitId=" + team)
                .andExpect(jsonPath("$.data.content[*].name").value(contains("임태현")));
        asAdmin("/api/employees?employmentTypeId=" + contract)
                .andExpect(jsonPath("$.data.content[*].name").value(contains("임태현")));
        asAdmin("/api/employees?orgUnitId=" + biz + "&employmentTypeId=" + contract)
                .andExpect(jsonPath("$.data.totalElements").value(0));
        asAdmin("/api/employees?keyword=유나")
                .andExpect(jsonPath("$.data.content[*].name").value(contains("신유나")));
        String employeeNo = jdbc.queryForObject("SELECT employee_no FROM employee WHERE id = ?", String.class, seller);
        asAdmin("/api/employees?keyword=" + employeeNo)
                .andExpect(jsonPath("$.data.content[*].id").value(contains((int) seller)));
    }

    @Test
    void 퇴직자는_기본으로_숨기고_재직상태_필터로_본다() throws Exception {
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", seller);
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", developer);

        asAdmin("/api/employees")
                .andExpect(jsonPath("$.data.content[*].name").value(contains("강하늘", "임태현", "관리자")));
        asAdmin("/api/employees?status=RESIGNED")
                .andExpect(jsonPath("$.data.content[*].name").value(contains("신유나")))
                .andExpect(jsonPath("$.data.content[0].status").value("RESIGNED"))
                .andExpect(jsonPath("$.data.content[0].todayStatus").value(nullValue()));
        asAdmin("/api/employees?status=ON_LEAVE,RESIGNED")
                .andExpect(jsonPath("$.data.content[*].name").value(contains("임태현", "신유나")));
        asAdmin("/api/employees?status=FIRED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
    }

    @Test
    void 오늘_상태는_재직상태와_오늘_근태로_정한다() throws Exception {
        attendance(devLead, "CHECKED_IN", "REMOTE", false);
        attendance(developer, "CHECKED_OUT", "OFFICE", true);
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", seller);
        attendance(seller, "CHECKED_IN", "OFFICE", false);   // 휴직이면 근태가 있어도 휴직이다

        asAdmin("/api/employees")
                .andExpect(jsonPath("$.data.content[*].todayStatus")
                        .value(contains("REMOTE", "OFF_WORK", "ON_LEAVE", "BEFORE_WORK")));
    }

    @Test
    void 정렬은_정해_둔_항목만_받는다() throws Exception {
        jdbc.update("UPDATE employee SET hire_date = DATE '2025-01-02' WHERE id = ?", developer);

        asAdmin("/api/employees?sort=hireDate,desc")
                .andExpect(jsonPath("$.data.content[0].name").value("관리자"))
                .andExpect(jsonPath("$.data.content[1].name").value("임태현"));
        asAdmin("/api/employees?sort=name,asc")
                .andExpect(jsonPath("$.data.content[*].name").value(contains("강하늘", "관리자", "신유나", "임태현")));
        // 모르는 정렬 항목은 무시하고 사원번호 순
        asAdmin("/api/employees?sort=bankAccountEnc,desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].name").value(contains("강하늘", "임태현", "신유나", "관리자")));
    }

    // ---------------------------------------------------------------- 연봉(BR-PAY-016)

    @Test
    void 연환산_연봉은_PAYROLL_READ_가_있을_때만_내려간다() throws Exception {
        jdbc.update("""
                INSERT INTO employee_salary (company_id, employee_id, salary_type, annual_salary, monthly_base,
                                             effective_from, reason, created_by)
                VALUES (?, ?, 'ANNUAL', 48000000, 4000000, DATE '2024-03-01', '입사', ?),
                       (?, ?, 'ANNUAL', 60000000, 5000000, DATE '2999-01-01', '예정 인상', ?),
                       (?, ?, 'MONTHLY', NULL, 3000000, DATE '2024-03-01', '입사', ?)
                """, company.id(), devLead, company.adminId(), company.id(), devLead, company.adminId(),
                company.id(), developer, company.adminId());

        // 최고 관리자: 연봉제는 연봉(아직 시작 안 한 인상은 제외), 월급제는 월 기본급 × 12, 미등록은 null
        asAdmin("/api/employees")
                .andExpect(jsonPath("$.data.content[0].annualSalary").value(48000000))
                .andExpect(jsonPath("$.data.content[1].annualSalary").value(36000000))
                .andExpect(jsonPath("$.data.content[2].annualSalary").value(nullValue()))
                .andExpect(jsonPath("$.data.content[2].annualSalary").hasJsonPath());
        // 직원 역할(팀 범위)에는 PAYROLL_READ 가 없어 필드 자체가 없다
        as(devLead, "/api/employees")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("강하늘"))
                .andExpect(jsonPath("$.data.content[0].annualSalary").doesNotHaveJsonPath());
    }

    // ---------------------------------------------------------------- 범위 · 권한 · 회사 격리

    @Test
    void 팀_범위는_내가_조직장인_조직과_하위_조직_직원만_보인다() throws Exception {
        as(devLead, "/api/employees")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].name").value(contains("강하늘", "임태현")));
        // 범위 밖 조직을 필터로 넣어도 범위 안만 나온다
        as(devLead, "/api/employees?orgUnitId=" + biz)
                .andExpect(jsonPath("$.data.totalElements").value(0));
        as(devLead, "/api/employees?orgUnitId=" + company.rootOrgUnitId())
                .andExpect(jsonPath("$.data.content[*].name").value(contains("강하늘", "임태현")));
        // 조직장이 아니면 팀 범위 권한이 있어도 대상이 없다
        as(developer, "/api/employees")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void EMPLOYEE_READ_가_없으면_목록을_볼_수_없다() throws Exception {
        jdbc.update("""
                DELETE FROM role_permission WHERE permission_code = 'EMPLOYEE_READ'
                  AND role_id = (SELECT id FROM role WHERE company_id = ? AND name = '직원')
                """, company.id());

        as(devLead, "/api/employees")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 다른_회사의_직원은_목록에_섞이지_않는다() throws Exception {
        TestFixture.Company other = fixture.company("직원목록테스트타사");
        String otherToken = fixture.token(other.adminId(), other.id());

        mvc.perform(get("/api/employees").header("Authorization", otherToken))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(other.adminId()));
        // 다른 회사의 조직 ID 로 걸러도 아무것도 나오지 않는다
        mvc.perform(get("/api/employees?orgUnitId=" + dev).header("Authorization", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ---------------------------------------------------------------- 개인 페이지

    @Test
    void 개인_페이지는_내_정보와_추가_항목_휴가_잔여를_담고_인사_메모는_없다() throws Exception {
        long skill = jdbc.queryForObject("""
                INSERT INTO employee_field_def (company_id, name, field_type, is_multiple, sort_order)
                VALUES (?, '기술 스택', 'TEXT', TRUE, 1) RETURNING id
                """, Long.class, company.id());
        jdbc.update("INSERT INTO employee_field_value (company_id, employee_id, field_def_id, seq, value) "
                + "VALUES (?, ?, ?, 1, 'Java'), (?, ?, ?, 2, 'SQL')",
                company.id(), developer, skill, company.id(), developer, skill);
        jdbc.update("""
                UPDATE employee SET phone = '010-1000-0008', gender = 'MALE', hr_memo = '비공개 메모',
                       bank_name = '국민', emergency_name = '임부모' WHERE id = ?
                """, developer);
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'", Long.class,
                company.id());
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, ?, 'REGULAR', 15)
                """, company.id(), developer, annual, LocalDate.now(ClockConfig.ZONE).getYear());

        as(developer, "/api/me/profile")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(developer))
                .andExpect(jsonPath("$.data.name").value("임태현"))
                .andExpect(jsonPath("$.data.employeeNo").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.phone").value("010-1000-0008"))
                .andExpect(jsonPath("$.data.gender").value("MALE"))
                .andExpect(jsonPath("$.data.emergencyName").value("임부모"))
                .andExpect(jsonPath("$.data.hireDate").value("2024-03-01"))
                .andExpect(jsonPath("$.data.orgUnitId").value(team))
                .andExpect(jsonPath("$.data.orgUnitName").value("백엔드팀"))
                .andExpect(jsonPath("$.data.jobGradeName").value(nullValue()))
                .andExpect(jsonPath("$.data.employmentTypeName").value("정규직"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.payrollEligible").value(true))
                .andExpect(jsonPath("$.data.profileFileId").value(nullValue()))
                .andExpect(jsonPath("$.data.fieldValues[*].value").value(contains("Java", "SQL")))
                .andExpect(jsonPath("$.data.fieldValues[0].fieldDefId").value(skill))
                .andExpect(jsonPath("$.data.fieldValues[0].name").value("기술 스택"))
                .andExpect(jsonPath("$.data.fieldValues[0].fieldType").value("TEXT"))
                .andExpect(jsonPath("$.data.fieldValues[1].seq").value(2))
                .andExpect(jsonPath("$.data.leaveBalances[0].leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.leaveBalances[0].remaining").value(15))
                .andExpect(jsonPath("$.data.hrMemo").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.bankName").doesNotHaveJsonPath());

        // 다른 사람으로 로그인하면 그 사람 것만 나온다
        as(seller, "/api/me/profile")
                .andExpect(jsonPath("$.data.id").value(seller))
                .andExpect(jsonPath("$.data.fieldValues.length()").value(0));
    }
}
