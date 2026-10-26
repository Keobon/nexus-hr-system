package com.nexuslabs.hr.domain.attendance;

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
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-10 근태 소명 — 직원이 지각 · 조퇴 · 퇴근미기록 · 결근인 날에 사유와 실제 시각을 내고, 인사 담당이 승인하면 근태가 정정된다.
 * 신청할 수 있는 날이 "오늘"에 따라 갈리므로 테스트용 시계를 쓴다. JPA 를 쓰는 기능이라 새 회사를 만들고 지우지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class AttendanceExplanationTest {

    /** 2030-03-04(월) 퇴근미기록 · 3/5(화) 지각 · 3/6(수) 결근 · 3/7(목) 정상. 오늘은 3/8(금) 12:00. */
    static final LocalDate MONDAY = TestClock.MONDAY;
    static final LocalDate TODAY = MONDAY.plusDays(4);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    TestFixture.Employee employee, other;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("근태소명테스트");
        employee = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        other = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        // 결근 판정은 직원 등록일부터 — 등록일을 3/4 로 맞춘다
        jdbc.update("UPDATE employee SET created_at = TIMESTAMPTZ '2030-03-04 08:00+09' WHERE company_id = ?",
                company.id());
        attendance(MONDAY, "MISSING_CHECKOUT", "09:00", null);
        attendance(MONDAY.plusDays(1), "CHECKED_OUT", "09:20", "18:00");
        attendance(MONDAY.plusDays(3), "CHECKED_OUT", "08:50", "18:10");
        clock.set(TODAY.atTime(12, 0));
    }

    private void attendance(LocalDate date, String status, String in, String out) {
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                                        check_in_method, check_out_method)
                VALUES (?, ?, ?, ?::attendance_status, 'OFFICE', (?::date + ?::time) AT TIME ZONE 'Asia/Seoul',
                        (?::date + ?::time) AT TIME ZONE 'Asia/Seoul', 'WEB', ?::record_method)
                """, company.id(), employee.id(), Date.valueOf(date), status, Date.valueOf(date), in,
                Date.valueOf(date), out, out == null ? null : "WEB");
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return as(company.adminId(), request);
    }

    private ResultActions explain(long employeeId, String body) throws Exception {
        return as(employeeId, post("/api/me/attendance-explanations").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long explainOk(String body) throws Exception {
        String res = explain(employee.id(), body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(res.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions approve(long id) throws Exception {
        return asAdmin(post("/api/attendance-explanations/" + id + "/approve"));
    }

    private ResultActions reject(long id, String body) throws Exception {
        return asAdmin(post("/api/attendance-explanations/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Map<String, Object> attendanceRow(LocalDate date) {
        return jdbc.queryForMap("""
                SELECT status::text AS status, work_type::text AS work_type,
                       to_char(check_in_at AT TIME ZONE 'Asia/Seoul', 'HH24:MI') AS check_in,
                       to_char(check_out_at AT TIME ZONE 'Asia/Seoul', 'HH24:MI') AS check_out,
                       check_out_method::text AS check_out_method, correction_reason, corrected_by
                FROM attendance WHERE employee_id = ? AND work_date = ?
                """, employee.id(), Date.valueOf(date));
    }

    @Test
    void 퇴근미기록_소명을_승인하면_근태가_정정되고_감사_로그가_남는다() throws Exception {
        long id = explainOk("""
                {"workDate": "2030-03-04", "reason": "퇴근 카드 미태깅", "checkOutAt": "2030-03-04T18:20:00+09:00"}""");

        // 인사 담당의 정정 대상에 소명 대기로 나오고, 할 일 건수에도 들어간다
        asAdmin(get("/api/attendances/corrections"))
                .andExpect(jsonPath("$.data[?(@.type == 'EXPLANATION')].explanation.id").value(contains((int) id)))
                .andExpect(jsonPath("$.data[?(@.type == 'EXPLANATION')].explanation.reason").value(contains("퇴근 카드 미태깅")))
                .andExpect(jsonPath("$.data[?(@.type == 'EXPLANATION')].explanation.checkOutAt")
                        .value(contains("2030-03-04T18:20:00+09:00")));
        asAdmin(get("/api/me")).andExpect(jsonPath("$.data.todos.attendanceCorrections").value(2));   // 퇴근미기록 + 소명

        approve(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").exists());

        Map<String, Object> row = attendanceRow(MONDAY);
        assertThat(row.get("status")).isEqualTo("CHECKED_OUT");
        assertThat(row.get("check_out")).isEqualTo("18:20");
        assertThat(row.get("check_out_method")).isEqualTo("ADMIN");
        assertThat(row.get("correction_reason")).isEqualTo("소명: 퇴근 카드 미태깅");
        assertThat(((Number) row.get("corrected_by")).longValue()).isEqualTo(company.adminId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'ATTENDANCE'",
                Long.class, company.id())).isEqualTo(1);
        // 정정 대상에서 빠진다(퇴근미기록 · 소명 둘 다)
        asAdmin(get("/api/attendances/corrections")).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 결근_소명은_출퇴근을_둘_다_내야_하고_승인하면_근태가_생긴다() throws Exception {
        explain(employee.id(), """
                {"workDate": "2030-03-06", "reason": "외부 미팅", "checkInAt": "2030-03-06T09:00:00+09:00"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.checkOutAt").exists());

        long id = explainOk("""
                {"workDate": "2030-03-06", "reason": "외부 미팅", "checkInAt": "2030-03-06T09:00:00+09:00",
                 "checkOutAt": "2030-03-06T18:00:00+09:00", "workType": "FIELD"}""");
        // 내 근태 날짜 행에 소명 상태가 보인다
        as(employee.id(), get("/api/me/attendances?month=2030-03"))
                .andExpect(jsonPath("$.data.days[5].status").value("ABSENT"))
                .andExpect(jsonPath("$.data.days[5].explanationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.days[6].explanationStatus").value(nullValue()));

        approve(id).andExpect(status().isOk());
        Map<String, Object> row = attendanceRow(MONDAY.plusDays(2));
        assertThat(row.get("status")).isEqualTo("CHECKED_OUT");
        assertThat(row.get("work_type")).isEqualTo("FIELD");
        assertThat(row.get("check_in")).isEqualTo("09:00");
        assertThat(row.get("check_out")).isEqualTo("18:00");
        as(employee.id(), get("/api/me/attendances?month=2030-03"))
                .andExpect(jsonPath("$.data.days[5].status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.days[5].explanationStatus").value("APPROVED"));
    }

    @Test
    void 사유만_낸_지각_소명은_승인해도_기록이_그대로다() throws Exception {
        long id = explainOk("{\"workDate\": \"2030-03-05\", \"reason\": \"지하철 지연(지연 증명서 있음)\"}");
        approve(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));

        Map<String, Object> row = attendanceRow(MONDAY.plusDays(1));
        assertThat(row.get("check_in")).isEqualTo("09:20");
        assertThat(row.get("correction_reason")).isEqualTo("소명: 지하철 지연(지연 증명서 있음)");   // 확인만 남는다
    }

    @Test
    void 반려는_사유가_필요하고_반려된_날은_다시_낼_수_있다() throws Exception {
        long id = explainOk("{\"workDate\": \"2030-03-05\", \"reason\": \"늦잠\"}");
        reject(id, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        reject(id, "{\"reason\": \"사유가 인정되지 않습니다\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("사유가 인정되지 않습니다"));

        as(employee.id(), get("/api/me/attendance-explanations?month=2030-03"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].rejectReason").value("사유가 인정되지 않습니다"));
        explain(employee.id(), "{\"workDate\": \"2030-03-05\", \"reason\": \"버스 사고로 지연\"}")
                .andExpect(status().isCreated());
        assertThat(attendanceRow(MONDAY.plusDays(1)).get("correction_reason")).isNull();   // 반려는 기록을 건드리지 않는다
    }

    @Test
    void 대기_중인_소명만_본인이_철회한다() throws Exception {
        long id = explainOk("{\"workDate\": \"2030-03-05\", \"reason\": \"늦잠\"}");
        as(other.id(), post("/api/me/attendance-explanations/" + id + "/withdraw"))
                .andExpect(status().isNotFound());
        as(employee.id(), post("/api/me/attendance-explanations/" + id + "/withdraw"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        as(employee.id(), post("/api/me/attendance-explanations/" + id + "/withdraw"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        approve(id).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        explain(employee.id(), "{\"workDate\": \"2030-03-05\", \"reason\": \"늦잠\"}").andExpect(status().isCreated());
    }

    @Test
    void 소명할_근태가_아닌_날과_중복과_정산된_달은_거부한다() throws Exception {
        // 정상 근무한 날 · 오늘 · 주말
        for (String date : new String[]{"2030-03-07", "2030-03-08", "2030-03-02"}) {
            explain(employee.id(), "{\"workDate\": \"" + date + "\", \"reason\": \"확인 부탁\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        }
        explain(employee.id(), "{\"workDate\": \"2030-03-05\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        explain(employee.id(), """
                {"workDate": "2030-03-04", "reason": "확인", "checkInAt": "2030-03-04T18:00:00+09:00",
                 "checkOutAt": "2030-03-04T09:00:00+09:00"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkOutAt").exists());
        // 승인 때 정정 규칙에 막힐 값은 신청할 때 거부한다 — 출근은 그날, 퇴근은 다음 날까지
        explain(employee.id(), """
                {"workDate": "2030-03-04", "reason": "확인", "checkInAt": "2030-03-05T09:00:00+09:00",
                 "checkOutAt": "2030-03-05T18:00:00+09:00"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkInAt").exists());
        explain(employee.id(), """
                {"workDate": "2030-03-04", "reason": "확인", "checkOutAt": "2030-03-06T01:00:00+09:00"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkOutAt").exists());

        explainOk("{\"workDate\": \"2030-03-05\", \"reason\": \"늦잠\"}");
        explain(employee.id(), "{\"workDate\": \"2030-03-05\", \"reason\": \"다시\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

        jdbc.update("""
                INSERT INTO payroll_run (company_id, pay_month, pay_date, company_name_snap, ceo_name_snap,
                                         business_reg_no_snap, company_address_snap, confirmed_by)
                VALUES (?, '2030-03', DATE '2030-03-25', '회사', '대표', '000-00-00000', '서울', ?)
                """, company.id(), company.adminId());
        explain(employee.id(), "{\"workDate\": \"2030-03-04\", \"reason\": \"퇴근 카드 미태깅\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PAY_MONTH_SETTLED"));
    }

    @Test
    void 승인과_반려는_ATTENDANCE_MANAGE_가_있어야_하고_다른_회사_건은_없다() throws Exception {
        long id = explainOk("{\"workDate\": \"2030-03-05\", \"reason\": \"늦잠\"}");
        as(other.id(), post("/api/attendance-explanations/" + id + "/approve"))
                .andExpect(status().isForbidden());

        TestFixture.Company another = fixture.company("근태소명다른회사");
        mvc.perform(post("/api/attendance-explanations/" + id + "/approve")
                        .header("Authorization", fixture.token(another.adminId(), another.id())))
                .andExpect(status().isNotFound());
    }
}
