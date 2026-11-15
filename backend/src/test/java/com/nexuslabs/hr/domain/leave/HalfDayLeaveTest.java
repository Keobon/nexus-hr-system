package com.nexuslabs.hr.domain.leave;

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

import java.time.LocalDate;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 반차(오전 · 오후, 0.5일, 2026-11-15). 휴가 기본 승인선은 조현우 → 서예린 → 강하늘. 시계는 2030-03-04(월) 09:00,
 * 근무시간은 회사 등록 기본값(월–금 09:00–18:00, 휴게 60분, 지각 허용 0분) — 반나절 = 4시간.
 * 오전 반차는 14:00(09:00 + 4시간 + 휴게 60분)부터, 오후 반차는 13:00(09:00 + 4시간)까지 일한다.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class HalfDayLeaveTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    DemoOrg org;
    long cid;
    long annual;
    long sick;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        annual = typeId("연차");
        sick = typeId("병가");
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2030, 'REGULAR', 10)""", cid, org.cho.id(), annual);
    }

    private long typeId(String name) {
        return jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = ?", Long.class, cid, name);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions apply(long typeId, String start, String end, String halfDay) throws Exception {
        String half = halfDay == null ? "" : ", \"halfDay\": \"" + halfDay + "\"";
        return as(org.cho, post("/api/me/leave-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"leaveTypeId": %d, "startDate": "%s", "endDate": "%s"%s}""".formatted(typeId, start, end, half)));
    }

    private long approved(String date, String halfDay) throws Exception {
        String body = apply(annual, date, date, halfDay).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        for (TestFixture.Employee approver : new TestFixture.Employee[]{org.seo, org.kang}) {
            long stepId = jdbc.queryForObject("""
                    SELECT id FROM approval_step WHERE company_id = ? AND work_type = 'LEAVE' AND target_id = ?
                      AND status = 'PENDING'""", Long.class, cid, id);
            as(approver, post("/api/approvals/" + stepId + "/approve").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }
        return id;
    }

    private void attend(String date, String in, String out) {
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                                        check_in_method, check_out_method)
                VALUES (?, ?, ?::date, 'CHECKED_OUT', 'OFFICE', (?::date + ?::time) AT TIME ZONE 'Asia/Seoul',
                        (?::date + ?::time) AT TIME ZONE 'Asia/Seoul', 'WEB', 'WEB')""",
                cid, org.cho.id(), date, date, in, date, out);
    }

    @Test
    void 반차는_0_5일로_미리보기_신청_잔여에_반영된다() throws Exception {
        as(org.cho, get("/api/me/leave-requests/preview").param("leaveTypeId", String.valueOf(annual))
                .param("startDate", "2030-03-11").param("endDate", "2030-03-11").param("halfDay", "AM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(0.5))
                .andExpect(jsonPath("$.data.balance.remaining").value(10))
                .andExpect(jsonPath("$.data.balance.remainingAfter").value(9.5));
        apply(annual, "2030-03-11", "2030-03-11", "AM").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.days").value(0.5))
                .andExpect(jsonPath("$.data.halfDay").value("AM"));
        // 잔여는 소수 첫째 자리, 반차가 없으면 지금처럼 정수
        as(org.cho, get("/api/me/leave-balances")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balances[0].granted").value(10))
                .andExpect(jsonPath("$.data.balances[0].pending").value(0.5))
                .andExpect(jsonPath("$.data.balances[0].remaining").value(9.5));
        // 같은 날 오후 반차는 겹침 — 하루를 쓰려면 종일로
        apply(annual, "2030-03-11", "2030-03-11", "PM").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PERIOD_OVERLAP"));
        apply(annual, "2030-03-12", "2030-03-12", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.days").value(1))
                .andExpect(jsonPath("$.data.halfDay").doesNotExist());
        as(org.cho, get("/api/me/leave-requests")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].days").value(contains(1, 0.5)));
        // 잔여는 0.5일 단위로 비교한다 — 부여를 2일로 줄이면 남은 0.5일에 반차 하나만 된다
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days, reason)
                VALUES (?, ?, ?, 2030, 'ADJUSTMENT', -8, '테스트')""", cid, org.cho.id(), annual);
        apply(annual, "2030-03-13", "2030-03-13", "PM").andExpect(status().isCreated());
        apply(annual, "2030-03-14", "2030-03-14", "PM").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAVE_INSUFFICIENT_BALANCE"));
    }

    @Test
    void 반차는_하루짜리_근무일_차감_종류만() throws Exception {
        apply(annual, "2030-03-11", "2030-03-12", "AM").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.halfDay").exists());
        apply(sick, "2030-03-11", "2030-03-11", "PM").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.halfDay").exists());
        apply(annual, "2030-03-09", "2030-03-09", "AM").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LEAVE_ZERO_DAYS"));
        apply(annual, "2030-03-11", "2030-03-11", "EVENING").andExpect(status().isBadRequest());
    }

    @Test
    void 반차는_휴가_근태를_만들지_않고_반나절_기준으로_판정한다() throws Exception {
        long am = approved("2030-03-11", "AM");
        approved("2030-03-12", "PM");
        approved("2030-03-13", "AM");
        approved("2030-03-14", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance WHERE leave_request_id = ?", Integer.class, am)).isZero();
        assertThat(jdbc.queryForObject("SELECT status::text FROM leave_request WHERE id = ?", String.class, am))
                .isEqualTo("APPROVED");

        attend("2030-03-11", "14:00", "18:00");   // 오전 반차 — 14:00 출근은 지각 아님, 휴게 없이 4시간
        attend("2030-03-12", "09:00", "13:00");   // 오후 반차 — 13:00 퇴근은 조퇴 아님
        // 03-13 오전 반차인데 기록 없음 → 반나절 결근, 03-14 종일 휴가
        clock.set(LocalDate.of(2030, 3, 20).atTime(9, 0));
        String day = "$.data.days[?(@.date == '%s')].%s";
        as(org.cho, get("/api/me/attendances").param("month", "2030-03")).andExpect(status().isOk())
                .andExpect(jsonPath(day.formatted("2030-03-11", "halfDay")).value(contains("AM")))
                .andExpect(jsonPath(day.formatted("2030-03-11", "late")).value(contains(false)))
                .andExpect(jsonPath(day.formatted("2030-03-11", "earlyLeave")).value(contains(false)))
                .andExpect(jsonPath(day.formatted("2030-03-11", "workMinutes")).value(contains(240)))
                .andExpect(jsonPath(day.formatted("2030-03-11", "overtimeMinutes")).value(contains(0)))
                .andExpect(jsonPath(day.formatted("2030-03-12", "halfDay")).value(contains("PM")))
                .andExpect(jsonPath(day.formatted("2030-03-12", "earlyLeave")).value(contains(false)))
                .andExpect(jsonPath(day.formatted("2030-03-12", "workMinutes")).value(contains(240)))
                .andExpect(jsonPath(day.formatted("2030-03-13", "status")).value(contains("ABSENT")))
                .andExpect(jsonPath(day.formatted("2030-03-13", "halfDay")).value(contains("AM")))
                .andExpect(jsonPath(day.formatted("2030-03-14", "status")).value(contains("ON_VACATION")));

        // 기준을 넘기면 지각 · 조퇴 — 오전 반차 14:30 출근, 오후 반차 12:30 퇴근
        jdbc.update("UPDATE attendance SET check_in_at = check_in_at + interval '30 minutes' WHERE employee_id = ? AND work_date = '2030-03-11'", org.cho.id());
        jdbc.update("UPDATE attendance SET check_out_at = check_out_at - interval '30 minutes' WHERE employee_id = ? AND work_date = '2030-03-12'", org.cho.id());
        as(org.cho, get("/api/me/attendances").param("month", "2030-03"))
                .andExpect(jsonPath(day.formatted("2030-03-11", "late")).value(contains(true)))
                .andExpect(jsonPath(day.formatted("2030-03-12", "earlyLeave")).value(contains(true)));
    }

    @Test
    void 이번_달_휴가_사용은_반차를_0_5일로_센다() throws Exception {
        approved("2030-03-11", "AM");
        approved("2030-03-12", null);
        TestFixture.Employee admin = new TestFixture.Employee(org.company.adminId(), cid, org.company.adminEmail());
        as(admin, get("/api/dashboard/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.leaveUsageThisMonth[0].days").value(1.5))
                .andExpect(jsonPath("$.data.company.onVacationThisMonth[*].days").value(contains(0.5, 1)));
    }
}
