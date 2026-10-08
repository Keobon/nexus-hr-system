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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-01·02 출근 · 퇴근 · 근무 형태 변경 · 오늘 조회(API 설계서 7.1, BR-ATT-001–003).
 * 시각에 따라 결과가 달라지므로 이 테스트만 시계를 바꿔 끼운다. JPA 를 쓰는 기능이라 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class MyAttendanceTest {

    /** 회사를 이날(월요일) 등록하므로 기본 근무시간(09:00–18:00, 월–금, 야간 22:00–06:00)이 이날부터다. */
    static final LocalDate MONDAY = TestClock.MONDAY;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    TestFixture.Employee employee;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("출퇴근테스트");
        employee = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    /** 시계를 옮기면 전에 발급한 토큰이 만료될 수 있어 요청마다 새로 발급한다. */
    private ResultActions as(TestFixture.Employee who, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(who.id(), who.companyId())));
    }

    private ResultActions me(MockHttpServletRequestBuilder request) throws Exception {
        return as(employee, request);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions checkIn() throws Exception {
        return me(post("/api/me/attendance/check-in"));
    }

    private ResultActions checkOut() throws Exception {
        return me(post("/api/me/attendance/check-out"));
    }

    private ResultActions today() throws Exception {
        return me(get("/api/me/attendance/today"));
    }

    private Map<String, Object> row(LocalDate date) {
        return jdbc.queryForMap("""
                SELECT status::text AS status, work_type::text AS work_type, place_memo,
                       check_in_method::text AS check_in_method, check_out_method::text AS check_out_method,
                       check_in_lat, check_in_lng, check_out_lat,
                       to_char(check_in_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') AS check_in_at,
                       to_char(check_out_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') AS check_out_at
                FROM attendance WHERE company_id = ? AND employee_id = ? AND work_date = ?
                """, company.id(), employee.id(), Date.valueOf(date));
    }

    /** 출근만 하고 퇴근하지 않은 지난 근태. */
    private void openRow(LocalDate date) {
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_in_method)
                VALUES (?, ?, ?, 'CHECKED_IN', 'OFFICE', (?::date + time '09:00') AT TIME ZONE 'Asia/Seoul', 'WEB')
                """, company.id(), employee.id(), Date.valueOf(date), Date.valueOf(date));
    }

    private void vacationRow(LocalDate date) {
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, company.id());
        long leaveId = jdbc.queryForObject("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date,
                                           days, status)
                VALUES (?, ?, ?, ?, ?, ?, 1, 'APPROVED') RETURNING id
                """, Long.class, company.id(), employee.id(), annual, date.getYear(), Date.valueOf(date), Date.valueOf(date));
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, check_in_method, leave_request_id)
                VALUES (?, ?, ?, 'ON_VACATION', 'SYSTEM', ?)
                """, company.id(), employee.id(), Date.valueOf(date), leaveId);
    }

    // ---------------------------------------------------------------- 출근(F-ATT-01)

    @Test
    void 출근_전에는_오늘_날짜와_출근_전_상태를_보여_준다() throws Exception {
        assertThat(MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

        today().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.isWorkday").value(true))
                .andExpect(jsonPath("$.data.status").value("BEFORE_WORK"))
                .andExpect(jsonPath("$.data.workType").value(nullValue()))
                .andExpect(jsonPath("$.data.placeMemo").value(nullValue()))
                .andExpect(jsonPath("$.data.checkInAt").value(nullValue()))
                .andExpect(jsonPath("$.data.checkOutAt").value(nullValue()));
    }

    @Test
    void 본문_없이_출근하면_서버_시각으로_사내_근무가_기록된다() throws Exception {
        clock.set(MONDAY.atTime(8, 57, 30));

        checkIn().andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.workDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.status").value("OFFICE"))
                .andExpect(jsonPath("$.data.workType").value("OFFICE"))
                .andExpect(jsonPath("$.data.checkInAt").value("2030-03-04T08:57:30+09:00"))
                .andExpect(jsonPath("$.data.checkOutAt").value(nullValue()));

        assertThat(row(MONDAY)).containsEntry("status", "CHECKED_IN").containsEntry("check_in_method", "WEB")
                .containsEntry("check_in_at", "2030-03-04 08:57:30").containsEntry("check_in_lat", null);
        today().andExpect(jsonPath("$.data.status").value("OFFICE"));
    }

    @Test
    void 근무_형태_장소_위치를_함께_기록하고_모바일_브라우저면_기록_방식이_모바일이다() throws Exception {
        me(json(post("/api/me/attendance/check-in"), """
                {"workType": "FIELD", "placeMemo": " 판교 협력사 미팅 ", "lat": 37.4012345678, "lng": 127.108765}""")
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FIELD"))
                .andExpect(jsonPath("$.data.placeMemo").value("판교 협력사 미팅"));

        Map<String, Object> saved = row(MONDAY);
        assertThat(saved).containsEntry("work_type", "FIELD").containsEntry("check_in_method", "MOBILE");
        assertThat(saved.get("check_in_lat").toString()).isEqualTo("37.401235");
        assertThat(saved.get("check_in_lng").toString()).isEqualTo("127.108765");
    }

    @Test
    void 이미_출근했거나_퇴근한_날은_다시_출근할_수_없다() throws Exception {
        checkIn().andExpect(status().isCreated());
        checkIn().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_ALREADY_CHECKED_IN"));

        clock.set(MONDAY.atTime(18, 0));
        checkOut().andExpect(status().isOk());
        checkIn().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_ALREADY_CHECKED_IN"));
    }

    @Test
    void 휴가일에는_출근도_퇴근도_할_수_없고_오늘_상태는_휴가다() throws Exception {
        vacationRow(MONDAY);

        checkIn().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_ON_LEAVE_OR_TRIP"));
        checkOut().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_ON_LEAVE_OR_TRIP"));
        today().andExpect(jsonPath("$.data.status").value("ON_VACATION"))
                .andExpect(jsonPath("$.data.checkInAt").value(nullValue()));
    }

    @Test
    void 휴직_중이면_출퇴근할_수_없고_오늘_상태는_휴직이다() throws Exception {
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", employee.id());

        checkIn().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_ACTIVE"));
        checkOut().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_ACTIVE"));
        today().andExpect(jsonPath("$.data.status").value("ON_LEAVE"));
    }

    @Test
    void 근무일이_아닌_날에도_출근할_수_있다() throws Exception {
        clock.set(MONDAY.plusDays(5).atTime(10, 0));   // 토요일

        checkIn().andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.workDate").value("2030-03-09"))
                .andExpect(jsonPath("$.data.isWorkday").value(false))
                .andExpect(jsonPath("$.data.status").value("OFFICE"));
    }

    @Test
    void 이전에_퇴근하지_않은_기록은_출근할_때_모두_퇴근미기록이_된다() throws Exception {
        // 지난주 목·금에 출근만 하고 퇴근하지 않았다. 수요일은 정상 퇴근
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                                        check_in_method, check_out_method)
                VALUES (?, ?, ?, 'CHECKED_OUT', 'OFFICE', TIMESTAMPTZ '2030-02-27 09:00+09', TIMESTAMPTZ '2030-02-27 18:00+09',
                        'WEB', 'WEB')
                """, company.id(), employee.id(), Date.valueOf(MONDAY.minusDays(5)));
        openRow(MONDAY.minusDays(4));
        openRow(MONDAY.minusDays(3));

        checkIn().andExpect(status().isCreated()).andExpect(jsonPath("$.data.workDate").value("2030-03-04"));

        assertThat(row(MONDAY.minusDays(5))).containsEntry("status", "CHECKED_OUT");
        assertThat(row(MONDAY.minusDays(4))).containsEntry("status", "MISSING_CHECKOUT");
        assertThat(row(MONDAY.minusDays(3))).containsEntry("status", "MISSING_CHECKOUT");
        assertThat(row(MONDAY)).containsEntry("status", "CHECKED_IN");
    }

    @Test
    void 출근_입력_검증() throws Exception {
        me(json(post("/api/me/attendance/check-in"), "{\"workType\": \"BUSINESS_TRIP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.workType").exists());
        me(json(post("/api/me/attendance/check-in"), "{\"workType\": \"HOME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
        me(json(post("/api/me/attendance/check-in"), "{\"lat\": 37.4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.lng").exists());
        me(json(post("/api/me/attendance/check-in"), "{\"lat\": 91, \"lng\": 127}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.lat").exists());
        me(json(post("/api/me/attendance/check-in"), "{\"placeMemo\": \"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.placeMemo").exists());

        today().andExpect(jsonPath("$.data.status").value("BEFORE_WORK"));
    }

    // ---------------------------------------------------------------- 퇴근(F-ATT-02)

    @Test
    void 퇴근하면_퇴근_시각이_기록되고_오늘_상태는_퇴근이다() throws Exception {
        checkIn();
        clock.set(MONDAY.atTime(18, 4));

        me(json(post("/api/me/attendance/check-out"), "{\"lat\": 37.5, \"lng\": 127.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_WORK"))
                .andExpect(jsonPath("$.data.workType").value("OFFICE"))
                .andExpect(jsonPath("$.data.checkInAt").value("2030-03-04T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.checkOutAt").value("2030-03-04T18:04:00+09:00"));

        assertThat(row(MONDAY)).containsEntry("status", "CHECKED_OUT").containsEntry("check_out_method", "WEB")
                .containsEntry("check_out_at", "2030-03-04 18:04:00");
        assertThat(row(MONDAY).get("check_out_lat").toString()).isEqualTo("37.500000");
    }

    @Test
    void 출근_없이_퇴근하거나_두_번_퇴근할_수_없다() throws Exception {
        checkOut().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_NO_CHECK_IN"));

        checkIn();
        clock.set(MONDAY.atTime(18, 0));
        checkOut().andExpect(status().isOk());
        checkOut().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_ALREADY_CHECKED_OUT"));
    }

    @Test
    void 출근_직후_같은_시각에_퇴근해도_기록된다() throws Exception {
        checkIn();

        checkOut().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkOutAt").value("2030-03-04T09:00:01+09:00"));
    }

    @Test
    void 자정을_넘겨도_야간_끝_전이면_어제_근태를_보여_주고_퇴근은_어제_근태를_닫는다() throws Exception {
        checkIn();
        clock.set(MONDAY.plusDays(1).atTime(1, 20));

        today().andExpect(jsonPath("$.data.workDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.status").value("OFFICE"))
                .andExpect(jsonPath("$.data.checkInAt").value("2030-03-04T09:00:00+09:00"));
        // 직원 목록의 오늘 상태도 같은 기준이다
        as(new TestFixture.Employee(company.adminId(), company.id(), company.adminEmail()), get("/api/employees"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + employee.id() + ")].todayStatus")
                        .value(contains("OFFICE")));

        checkOut().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.status").value("OFF_WORK"))
                .andExpect(jsonPath("$.data.checkOutAt").value("2030-03-05T01:20:00+09:00"));

        assertThat(row(MONDAY)).containsEntry("status", "CHECKED_OUT")
                .containsEntry("check_out_at", "2030-03-05 01:20:00");
        // 어제 근태를 닫았으니 이제 오늘(출근 전)을 보여 준다
        today().andExpect(jsonPath("$.data.workDate").value("2030-03-05"))
                .andExpect(jsonPath("$.data.status").value("BEFORE_WORK"));
    }

    @Test
    void 야간_끝이_지나면_어제_근태를_닫을_수_없고_출근하면_어제는_퇴근미기록이_된다() throws Exception {
        checkIn();
        clock.set(MONDAY.plusDays(1).atTime(6, 0));

        today().andExpect(jsonPath("$.data.workDate").value("2030-03-05"))
                .andExpect(jsonPath("$.data.status").value("BEFORE_WORK"))
                .andExpect(jsonPath("$.data.checkInAt").value(nullValue()));
        checkOut().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_NO_CHECK_IN"));
        assertThat(row(MONDAY)).containsEntry("status", "CHECKED_IN");

        clock.set(MONDAY.plusDays(1).atTime(8, 50));
        checkIn().andExpect(status().isCreated()).andExpect(jsonPath("$.data.workDate").value("2030-03-05"));
        assertThat(row(MONDAY)).containsEntry("status", "MISSING_CHECKOUT");
    }

    @Test
    void 어제_퇴근한_사람은_자정이_지나면_오늘_출근_전이다() throws Exception {
        checkIn();
        clock.set(MONDAY.atTime(23, 30));
        checkOut();
        clock.set(MONDAY.plusDays(1).atTime(0, 30));

        today().andExpect(jsonPath("$.data.workDate").value("2030-03-05"))
                .andExpect(jsonPath("$.data.status").value("BEFORE_WORK"));
        checkOut().andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_NO_CHECK_IN"));
    }

    // ---------------------------------------------------------------- 근무 형태 변경(F-ATT-01)

    @Test
    void 퇴근_전까지_근무_형태와_장소를_바꿀_수_있다() throws Exception {
        checkIn();

        me(json(patch("/api/me/attendance/today"), "{\"workType\": \"FIELD\", \"placeMemo\": \"고객사\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FIELD"))
                .andExpect(jsonPath("$.data.placeMemo").value("고객사"));
        // 보낸 필드만 바꾼다 — 근무 형태는 그대로, 장소만 비운다
        me(json(patch("/api/me/attendance/today"), "{\"placeMemo\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workType").value("FIELD"))
                .andExpect(jsonPath("$.data.placeMemo").value(nullValue()));
        me(json(patch("/api/me/attendance/today"), "{\"workType\": \"REMOTE\"}"))
                .andExpect(status().isOk());

        assertThat(row(MONDAY)).containsEntry("work_type", "REMOTE").containsEntry("place_memo", null)
                .containsEntry("status", "CHECKED_IN");
    }

    @Test
    void 근무_형태_변경_거부() throws Exception {
        me(json(patch("/api/me/attendance/today"), "{\"workType\": \"REMOTE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_NO_CHECK_IN"));

        checkIn();
        me(json(patch("/api/me/attendance/today"), "{\"workType\": \"BUSINESS_TRIP\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
        me(json(patch("/api/me/attendance/today"), "{\"workType\": null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.workType").exists());
        me(json(patch("/api/me/attendance/today"), "{\"checkInAt\": \"2030-03-04T08:00:00+09:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkInAt").exists());
        me(json(patch("/api/me/attendance/today"), "{\"placeMemo\": \"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.placeMemo").exists());

        clock.set(MONDAY.atTime(18, 0));
        checkOut();
        me(json(patch("/api/me/attendance/today"), "{\"workType\": \"REMOTE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_ALREADY_CHECKED_OUT"));
    }

    @Test
    void 자정을_넘긴_동안의_근무_형태_변경은_어제_근태에_적용한다() throws Exception {
        checkIn();
        clock.set(MONDAY.plusDays(1).atTime(0, 10));

        me(json(patch("/api/me/attendance/today"), "{\"workType\": \"REMOTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workDate").value("2030-03-04"));

        assertThat(row(MONDAY)).containsEntry("work_type", "REMOTE");
    }

    // ---------------------------------------------------------------- 본인만

    @Test
    void 다른_직원의_근태에는_영향이_없다() throws Exception {
        TestFixture.Employee other = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        checkIn();

        as(other, get("/api/me/attendance/today")).andExpect(jsonPath("$.data.status").value("BEFORE_WORK"));
        as(other, post("/api/me/attendance/check-out"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATT_NO_CHECK_IN"));
        mvc.perform(get("/api/me/attendance/today")).andExpect(status().isUnauthorized());
    }
}
