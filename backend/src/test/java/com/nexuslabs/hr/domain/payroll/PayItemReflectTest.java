package com.nexuslabs.hr.domain.payroll;

import com.jayway.jsonpath.JsonPath;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회사 지침에 따라 수당 · 공제 항목을 추가하거나 지우면 다음 급여 계산의 명세서 줄에 바로 반영된다(10/15 교수님 질문).
 * 이미 확정된 명세서는 그때 줄 그대로 남는다. 급여 정산 자체의 계산은 PayrollTest 가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class PayItemReflectTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    TestFixture.Employee kim;

    @BeforeEach
    void setUp() {
        clock.set(LocalDateTime.of(2030, 4, 2, 10, 0));
        company = fixture.company("항목반영테스트");
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        // 결근은 max(입사일, 등록일)부터 판정한다 — 출근 기록 없이 3 · 4월을 계산하므로 등록일을 그 뒤로 둬 결근 공제를 없앤다
        jdbc.update("UPDATE employee SET created_at = TIMESTAMPTZ '2030-05-01 00:00+09' WHERE id = ?", kim.id());
        jdbc.update("""
                INSERT INTO employee_salary (company_id, employee_id, salary_type, monthly_base, effective_from, reason, created_by)
                VALUES (?, ?, 'MONTHLY', 3000000, DATE '2024-03-01', '등록', ?)""", company.id(), kim.id(), company.adminId());
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return as(company.adminId(), request);
    }

    private long item(String name, String kind, long amount, int sortOrder) throws Exception {
        String body = "{\"name\": \"%s\", \"itemKind\": \"%s\", \"calcMethod\": \"FIXED\", \"defaultAmount\": %d, \"sortOrder\": %d}"
                .formatted(name, kind, amount, sortOrder);
        String res = admin(post("/api/pay-items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private ResultActions preview(String payMonth) throws Exception {
        return admin(post("/api/payroll-runs/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payMonth\": \"%s\"}".formatted(payMonth)));
    }

    private String line(String name) {
        return "$.data.employees[?(@.employeeId == %d)].lines[?(@.name == '%s')]".formatted(kim.id(), name);
    }

    @Test
    void 수당_공제를_추가하면_다음_계산부터_명세서_줄에_나오고_지우면_빠진다() throws Exception {
        preview("2030-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath(line("자격수당")).doesNotExist())
                .andExpect(jsonPath(line("동호회비")).doesNotExist());

        item("자격수당", "EARNING", 50_000, 30);
        long club = item("동호회비", "DEDUCTION", 10_000, 40);
        preview("2030-03")
                .andExpect(jsonPath(line("자격수당") + ".amount").value(50_000))
                .andExpect(jsonPath(line("자격수당") + ".itemKind").value("EARNING"))
                .andExpect(jsonPath(line("동호회비") + ".amount").value(10_000))
                .andExpect(jsonPath(line("동호회비") + ".itemKind").value("DEDUCTION"));

        // 한 번도 쓰이지 않은 항목은 실제로 지워지고 계산에서 빠진다
        admin(delete("/api/pay-items/" + club)).andExpect(jsonPath("$.data.result").value("DELETED"));
        preview("2030-03")
                .andExpect(jsonPath(line("자격수당") + ".amount").value(50_000))
                .andExpect(jsonPath(line("동호회비")).doesNotExist());
    }

    @Test
    void 확정된_명세서에_쓰인_항목을_지우면_비활성화되어_다음_달부터_빠지고_확정된_명세서는_그대로다() throws Exception {
        long license = item("자격수당", "EARNING", 50_000, 30);
        admin(post("/api/payroll-runs").contentType(MediaType.APPLICATION_JSON).content("{\"payMonth\": \"2030-03\"}"))
                .andExpect(status().isCreated());
        long march = jdbc.queryForObject("SELECT id FROM paystub WHERE company_id = ? AND employee_id = ?",
                Long.class, company.id(), kim.id());

        admin(delete("/api/pay-items/" + license)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        item("직책수당", "EARNING", 100_000, 31);

        clock.set(LocalDateTime.of(2030, 5, 2, 10, 0));
        preview("2030-04")
                .andExpect(jsonPath(line("자격수당")).doesNotExist())
                .andExpect(jsonPath(line("직책수당") + ".amount").value(100_000));

        as(kim.id(), get("/api/me/payslips/" + march))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[?(@.name == '자격수당')].amount").value(50_000))
                .andExpect(jsonPath("$.data.lines[?(@.name == '직책수당')]").doesNotExist());
    }
}
