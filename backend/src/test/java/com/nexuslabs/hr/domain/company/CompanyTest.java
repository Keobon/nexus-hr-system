package com.nexuslabs.hr.domain.company;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-COMP-02 회사 기본정보 · 변경 이력 (API 설계서 3장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("회사정보테스트");
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private ResultActions patchCompany(String body) throws Exception {
        return asAdmin(patch("/api/company").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String staffToken() {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        return fixture.token(staff.id(), company.id());
    }

    private int historyCount() {
        return jdbc.queryForObject("SELECT count(*) FROM company_change_history WHERE company_id = ?",
                Integer.class, company.id());
    }

    private static final String CHANGE = "\"change\": {\"effectiveDate\": \"2026-10-01\", \"reason\": \"대표이사 변경\"}";

    // ---------------------------------------------------------------- 조회

    @Test
    void COMPANY_MANAGE_가_있으면_전체_필드를_본다() throws Exception {
        asAdmin(get("/api/company"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(company.id()))
                .andExpect(jsonPath("$.data.name").value("회사정보테스트"))
                .andExpect(jsonPath("$.data.businessRegNo").exists())
                .andExpect(jsonPath("$.data.ceoName").value("대표"))
                .andExpect(jsonPath("$.data.payDay").value(25))
                .andExpect(jsonPath("$.data.fiscalYearStartMonth").value(1))
                .andExpect(jsonPath("$.data.setupCompleted").value(false))
                .andExpect(jsonPath("$.data.employeeNoPrefix").value(nullValue()))
                .andExpect(jsonPath("$.data.logoFileId").value(nullValue()));
    }

    @Test
    void 권한이_없으면_표시용_필드만_본다() throws Exception {
        mvc.perform(get("/api/company").header("Authorization", staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("회사정보테스트"))
                .andExpect(jsonPath("$.data.ceoName").value("대표"))
                .andExpect(jsonPath("$.data.address").value("서울"))
                .andExpect(jsonPath("$.data.phone").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.logoFileId").hasJsonPath())
                .andExpect(jsonPath("$.data.businessRegNo").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.payDay").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.fiscalYearStartMonth").doesNotHaveJsonPath());
    }

    // ---------------------------------------------------------------- 수정

    @Test
    void 수정은_보낸_필드만_바꾸고_이력_대상이_아니면_change_가_필요_없다() throws Exception {
        patchCompany("""
                {"payDay": 10, "employeeNoPrefix": "NX-", "website": "https://nexus.example", "nameEn": " Nexus ",
                 "corpRegNo": "1101111234567", "foundedDate": "2020-03-02", "fax": "02-111-2222"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payDay").value(10))
                .andExpect(jsonPath("$.data.employeeNoPrefix").value("NX-"))
                .andExpect(jsonPath("$.data.website").value("https://nexus.example"))
                .andExpect(jsonPath("$.data.nameEn").value("Nexus"))
                .andExpect(jsonPath("$.data.corpRegNo").value("110111-1234567"))
                .andExpect(jsonPath("$.data.foundedDate").value("2020-03-02"))
                .andExpect(jsonPath("$.data.name").value("회사정보테스트"))
                .andExpect(jsonPath("$.data.ceoName").value("대표"));
        assertThat(historyCount()).isZero();

        // null 을 보내면 비우고, 안 보낸 값은 그대로다
        patchCompany("{\"fax\": null, \"employeeNoPrefix\": \"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fax").value(nullValue()))
                .andExpect(jsonPath("$.data.employeeNoPrefix").value(nullValue()))
                .andExpect(jsonPath("$.data.payDay").value(10))
                .andExpect(jsonPath("$.data.website").value("https://nexus.example"));
    }

    @Test
    void 회사명과_대표자명을_바꾸면_항목마다_변경_이력이_남는다() throws Exception {
        patchCompany("{\"name\": \"새회사 주식회사\", \"ceoName\": \"최민호\", \"payDay\": 20, " + CHANGE + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새회사 주식회사"))
                .andExpect(jsonPath("$.data.ceoName").value("최민호"));
        patchCompany("""
                {"address": "부산", "change": {"effectiveDate": "2026-11-01", "reason": "본점 이전"}}
                """).andExpect(status().isOk());

        asAdmin(get("/api/company/change-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].field").value("ADDRESS"))
                .andExpect(jsonPath("$.data[0].oldValue").value("서울"))
                .andExpect(jsonPath("$.data[0].newValue").value("부산"))
                .andExpect(jsonPath("$.data[0].effectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$.data[0].reason").value("본점 이전"))
                .andExpect(jsonPath("$.data[0].document").value(nullValue()))
                .andExpect(jsonPath("$.data[0].changedBy.id").value(company.adminId()))
                .andExpect(jsonPath("$.data[0].changedBy.name").value("관리자"))
                .andExpect(jsonPath("$.data[0].createdAt").exists())
                .andExpect(jsonPath("$.data[1].field").value("CEO_NAME"))
                .andExpect(jsonPath("$.data[2].field").value("NAME"))
                .andExpect(jsonPath("$.data[2].oldValue").value("회사정보테스트"))
                .andExpect(jsonPath("$.data[2].newValue").value("새회사 주식회사"));
    }

    @Test
    void 이력_대상을_바꾸는데_change_가_없으면_거부하고_값이_같으면_이력을_만들지_않는다() throws Exception {
        patchCompany("{\"name\": \"바뀐이름\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.change").exists());
        patchCompany("{\"name\": \"바뀐이름\", \"change\": {\"effectiveDate\": \"2026-10-01\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['change.reason']").exists());
        patchCompany("{\"ceoName\": \"새대표\", \"change\": {\"reason\": \"변경\", \"effectiveDate\": \"10월 1일\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['change.effectiveDate']").exists());

        // 지금과 같은 값을 보내면 바뀐 것이 아니다
        patchCompany("{\"name\": \"회사정보테스트\", \"ceoName\": \"대표\", \"address\": \" 서울 \"}")
                .andExpect(status().isOk());
        assertThat(historyCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM company WHERE id = ?", String.class, company.id()))
                .isEqualTo("회사정보테스트");
    }

    @Test
    void 사업자등록번호는_형식을_맞춰_저장하고_다른_회사와_겹치면_거부한다() throws Exception {
        TestFixture.Company other = fixture.company("회사정보테스트타사");
        String otherRegNo = jdbc.queryForObject("SELECT business_reg_no FROM company WHERE id = ?", String.class,
                other.id());

        patchCompany("{\"businessRegNo\": \"" + otherRegNo + "\", " + CHANGE + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_REG_NO_DUPLICATE"));
        patchCompany("{\"businessRegNo\": \"12345\", " + CHANGE + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.businessRegNo").exists());

        String digits = "9" + "%09d".formatted(company.id() % 1_000_000_000L);
        String formatted = digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
        patchCompany("{\"businessRegNo\": \"" + digits + "\", " + CHANGE + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessRegNo").value(formatted));
        asAdmin(get("/api/company/change-history"))
                .andExpect(jsonPath("$.data[*].field").value(contains("BUSINESS_REG_NO")))
                .andExpect(jsonPath("$.data[0].newValue").value(formatted));
    }

    @Test
    void 휴가가_부여된_뒤에는_회계연도_시작월을_바꿀_수_없다() throws Exception {
        patchCompany("{\"fiscalYearStartMonth\": 4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fiscalYearStartMonth").value(4));

        long leaveTypeId = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, company.id());
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2026, 'REGULAR', 15)
                """, company.id(), company.adminId(), leaveTypeId);

        patchCompany("{\"fiscalYearStartMonth\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        // 같은 값을 다시 보내는 것과 다른 항목 수정은 된다
        patchCompany("{\"fiscalYearStartMonth\": 4, \"payDay\": 15}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payDay").value(15));
    }

    @Test
    void 잘못된_값은_항목별로_알려준다() throws Exception {
        patchCompany("""
                {"name": null, "payDay": 32, "fiscalYearStartMonth": 0, "email": "메일아님", "employeeNoPrefix": "NX_2026",
                 "foundedDate": "어제", "homepage": "x"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.homepage").exists());
        patchCompany("""
                {"payDay": 32, "fiscalYearStartMonth": 0, "email": "메일아님", "employeeNoPrefix": "NX_2026",
                 "foundedDate": "어제", "phone": " "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.payDay").exists())
                .andExpect(jsonPath("$.error.fields.fiscalYearStartMonth").exists())
                .andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.employeeNoPrefix").exists())
                .andExpect(jsonPath("$.error.fields.foundedDate").exists())
                .andExpect(jsonPath("$.error.fields.phone").exists());
        patchCompany("{\"logoFileId\": 999999999}")
                .andExpect(status().isNotFound());
        patchCompany("{\"name\": \"근거서류\", \"change\": {\"effectiveDate\": \"2026-10-01\", \"reason\": \"변경\", "
                + "\"documentId\": 999999999}}")
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT pay_day FROM company WHERE id = ?", Integer.class, company.id()))
                .isEqualTo(25);
    }

    @Test
    void 수정하면_감사_로그가_남는다() throws Exception {
        patchCompany("{\"payDay\": 5}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE company_id = ? AND target_type = 'COMPANY' AND action = 'UPDATE' AND actor_id = ?
                  AND before_value ->> 'payDay' = '25' AND after_value ->> 'payDay' = '5'
                """, Integer.class, company.id(), company.adminId())).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 권한 · 회사 격리

    @Test
    void 수정과_변경_이력_조회는_COMPANY_MANAGE_가_있어야_한다() throws Exception {
        String staffToken = staffToken();
        mvc.perform(patch("/api/company").contentType(MediaType.APPLICATION_JSON).content("{\"payDay\": 1}")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/api/company/change-history").header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_회사의_정보와_이력은_섞이지_않는다() throws Exception {
        TestFixture.Company other = fixture.company("회사정보테스트타사");
        String otherToken = fixture.token(other.adminId(), other.id());
        patchCompany("{\"name\": \"우리회사\", " + CHANGE + "}").andExpect(status().isOk());

        mvc.perform(get("/api/company").header("Authorization", otherToken))
                .andExpect(jsonPath("$.data.id").value(other.id()))
                .andExpect(jsonPath("$.data.name").value("회사정보테스트타사"));
        mvc.perform(get("/api/company/change-history").header("Authorization", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
