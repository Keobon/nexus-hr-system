package com.nexuslabs.hr.domain.attendance;

import com.nexuslabs.hr.domain.attendance.service.AttendanceCorrectionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-04 근태 정정 — 정정 대상 목록 · 정정 · 기록 없는 날의 근태 생성(API 설계서 7.1, 역할 분담 v2 2.3 D-12·20·21).
 * "오늘"에 따라 목록이 달라지므로 테스트용 시계를 쓴다. JPA 를 쓰는 기능이라 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class AttendanceCorrectionTest {

    /** 2030-03-04(월). 오늘은 그 주 금요일(3/8) 12:00 으로 둔다. */
    static final LocalDate MONDAY = TestClock.MONDAY;
    static final LocalDate TODAY = MONDAY.plusDays(4);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;
    @Autowired AttendanceCorrectionService correctionService;

    TestFixture.Company company;
    TestFixture.Employee employee;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("근태정정테스트");
        employee = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        clock.set(TODAY.atTime(12, 0));
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    /** 회사를 등록한 최고 관리자 — ATTENDANCE_MANAGE 가 있다. */
    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return as(company.adminId(), request);
    }

    private ResultActions corrections() throws Exception {
        return asAdmin(get("/api/attendances/corrections"));
    }

    private ResultActions correct(long attendanceId, String body) throws Exception {
        return asAdmin(patch("/api/attendances/" + attendanceId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions create(String body) throws Exception {
        return asAdmin(post("/api/attendances").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 출근 09:00, 퇴근은 out 이 null 이면 없음. */
    private long attendance(LocalDate date, String status, String out) {
        return jdbc.queryForObject("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                                        check_in_method, check_out_method)
                VALUES (?, ?, ?, ?::attendance_status, 'OFFICE', (?::date + time '09:00') AT TIME ZONE 'Asia/Seoul',
                        (?::date + ?::time) AT TIME ZONE 'Asia/Seoul', 'WEB', ?::record_method)
                RETURNING id
                """, Long.class, company.id(), employee.id(), Date.valueOf(date), status, Date.valueOf(date),
                Date.valueOf(date), out, out == null ? null : "WEB");
    }

    /** 승인된 휴가. approvedAt("2030-03-08 10:00")에 마지막 단계가 승인됐다. */
    private long approvedLeave(LocalDate start, LocalDate end, String approvedAt) {
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, company.id());
        long leaveId = jdbc.queryForObject("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date,
                                           days, status)
                VALUES (?, ?, ?, ?, ?, ?, 1, 'APPROVED') RETURNING id
                """, Long.class, company.id(), employee.id(), annual, start.getYear(), Date.valueOf(start), Date.valueOf(end));
        jdbc.update("""
                INSERT INTO approval_step (company_id, work_type, target_id, step_order, approver_id, status, acted_at)
                VALUES (?, 'LEAVE', ?, 1, ?, 'APPROVED', ?::timestamp AT TIME ZONE 'Asia/Seoul')
                """, company.id(), leaveId, company.adminId(), approvedAt);
        return leaveId;
    }

    private long approvedTrip(LocalDate start, LocalDate end) {
        long tripId = jdbc.queryForObject("""
                INSERT INTO business_trip (company_id, employee_id, trip_type, destination, purpose, start_date, end_date,
                                           status)
                VALUES (?, ?, 'DOMESTIC', '부산', '고객사 미팅', ?, ?, 'APPROVED') RETURNING id
                """, Long.class, company.id(), employee.id(), Date.valueOf(start), Date.valueOf(end));
        jdbc.update("""
                INSERT INTO approval_step (company_id, work_type, target_id, step_order, approver_id, status, acted_at)
                VALUES (?, 'BUSINESS_TRIP', ?, 1, ?, 'APPROVED', TIMESTAMPTZ '2030-03-01 10:00+09')
                """, company.id(), tripId, company.adminId());
        return tripId;
    }

    private long tripRow(LocalDate date, long tripId) {
        return jdbc.queryForObject("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_method,
                                        business_trip_id)
                VALUES (?, ?, ?, 'ON_BUSINESS_TRIP', 'BUSINESS_TRIP', 'SYSTEM', ?) RETURNING id
                """, Long.class, company.id(), employee.id(), Date.valueOf(date), tripId);
    }

    private Map<String, Object> row(long attendanceId) {
        return jdbc.queryForMap("""
                SELECT status::text AS status, work_type::text AS work_type, leave_request_id, business_trip_id,
                       check_in_method::text AS check_in_method, check_out_method::text AS check_out_method,
                       correction_reason, corrected_by,
                       to_char(check_in_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI') AS check_in_at,
                       to_char(check_out_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI') AS check_out_at,
                       to_char(corrected_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI') AS corrected_at
                FROM attendance WHERE id = ? AND company_id = ?
                """, attendanceId, company.id());
    }

    // ---------------------------------------------------------------- 정정 대상 목록

    @Test
    void 정정_대상은_퇴근미기록과_지난_날의_출근_상태_기록이다() throws Exception {
        long missing = attendance(MONDAY, "MISSING_CHECKOUT", null);
        long stale = attendance(MONDAY.plusDays(1), "CHECKED_IN", null);
        attendance(MONDAY.plusDays(2), "CHECKED_OUT", "18:00");
        attendance(TODAY, "CHECKED_IN", null);         // 오늘 출근 중인 기록은 대상이 아니다

        corrections().andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].attendanceId").value(contains((int) missing, (int) stale)))
                .andExpect(jsonPath("$.data[*].type").value(contains("MISSING_CHECKOUT", "MISSING_CHECKOUT")))
                .andExpect(jsonPath("$.data[0].employeeId").value((int) employee.id()))
                .andExpect(jsonPath("$.data[0].workDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data[0].status").value("MISSING_CHECKOUT"))
                .andExpect(jsonPath("$.data[0].workType").value("OFFICE"))
                .andExpect(jsonPath("$.data[0].checkInAt").value("2030-03-04T09:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].checkOutAt").value(nullValue()))
                .andExpect(jsonPath("$.data[0].conflict").value(nullValue()))
                // 저장된 상태는 그대로 출근이다
                .andExpect(jsonPath("$.data[1].status").value("CHECKED_IN"));
        assertThat(correctionService.countCorrections(company.id())).isEqualTo(2);
    }

    @Test
    void 자정을_넘겨_이어지는_어제_근무는_야간_끝까지_정정_대상이_아니다() throws Exception {
        attendance(TODAY, "CHECKED_IN", null);

        clock.set(TODAY.plusDays(1).atTime(1, 0));
        corrections().andExpect(jsonPath("$.data", hasSize(0)));

        clock.set(TODAY.plusDays(1).atTime(7, 0));
        corrections().andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("MISSING_CHECKOUT"));
    }

    @Test
    void 승인된_휴가_출장_기간의_근무일에_출근_기록이_있으면_충돌이다() throws Exception {
        // 3/1(금)~3/5(화) 휴가인데 3/2(토)와 3/4(월)에 출근했다. 3/6~3/7 출장인데 3/7 에 출근했다
        long leaveId = approvedLeave(MONDAY.minusDays(3), MONDAY.plusDays(1), "2030-02-28 10:00");
        long tripId = approvedTrip(MONDAY.plusDays(2), MONDAY.plusDays(3));
        attendance(MONDAY.minusDays(2), "CHECKED_OUT", "15:00");       // 토요일 — 충돌이 아니다
        long onLeave = attendance(MONDAY, "CHECKED_OUT", "18:00");
        long onTrip = attendance(MONDAY.plusDays(3), "CHECKED_OUT", "18:00");
        tripRow(MONDAY.plusDays(2), tripId);                            // 출장 근태 자체는 대상이 아니다

        corrections().andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].attendanceId").value(contains((int) onLeave, (int) onTrip)))
                .andExpect(jsonPath("$.data[0].type").value("LEAVE_CONFLICT"))
                .andExpect(jsonPath("$.data[0].status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data[0].conflict.id").value((int) leaveId))
                .andExpect(jsonPath("$.data[0].conflict.name").value("연차"))
                .andExpect(jsonPath("$.data[0].conflict.startDate").value("2030-03-01"))
                .andExpect(jsonPath("$.data[0].conflict.endDate").value("2030-03-05"))
                .andExpect(jsonPath("$.data[1].type").value("TRIP_CONFLICT"))
                .andExpect(jsonPath("$.data[1].conflict.id").value((int) tripId))
                .andExpect(jsonPath("$.data[1].conflict.name").value("부산"));
        assertThat(correctionService.countCorrections(company.id())).isEqualTo(2);
    }

    @Test
    void 퇴근미기록이면서_충돌인_기록은_퇴근미기록_한_건으로만_나온다() throws Exception {
        approvedLeave(MONDAY, MONDAY, "2030-03-01 10:00");
        attendance(MONDAY, "MISSING_CHECKOUT", null);

        corrections().andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("MISSING_CHECKOUT"))
                .andExpect(jsonPath("$.data[0].conflict").value(nullValue()));
    }

    // ---------------------------------------------------------------- 정정

    @Test
    void 퇴근미기록을_퇴근으로_정정하면_목록에서_빠지고_감사_로그가_남는다() throws Exception {
        long id = attendance(MONDAY, "MISSING_CHECKOUT", null);

        correct(id, """
                {"status": "CHECKED_OUT", "checkOutAt": "2030-03-04T18:10:00+09:00", "workType": "REMOTE",
                 "reason": "퇴근을 찍지 않음 — 본인 확인"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendanceId").value((int) id))
                .andExpect(jsonPath("$.data.employeeId").value((int) employee.id()))
                .andExpect(jsonPath("$.data.workDate").value("2030-03-04"))
                .andExpect(jsonPath("$.data.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.workType").value("REMOTE"))
                .andExpect(jsonPath("$.data.checkInAt").value("2030-03-04T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.checkOutAt").value("2030-03-04T18:10:00+09:00"))
                .andExpect(jsonPath("$.data.correctionReason").value("퇴근을 찍지 않음 — 본인 확인"))
                .andExpect(jsonPath("$.data.correctedAt").value("2030-03-08T12:00:00+09:00"))
                .andExpect(jsonPath("$.data.warning").value(nullValue()));

        assertThat(row(id)).containsEntry("status", "CHECKED_OUT").containsEntry("work_type", "REMOTE")
                .containsEntry("check_out_at", "2030-03-04 18:10").containsEntry("check_in_method", "ADMIN")
                .containsEntry("check_out_method", "ADMIN").containsEntry("corrected_by", company.adminId())
                .containsEntry("corrected_at", "2030-03-08 12:00");
        corrections().andExpect(jsonPath("$.data", hasSize(0)));
        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT action::text AS action, actor_id, before_value ->> 'status' AS before_status,
                       after_value ->> 'status' AS after_status
                FROM audit_log WHERE company_id = ? AND target_type = 'ATTENDANCE' AND target_id = ?
                """, company.id(), id);
        assertThat(audit).containsEntry("action", "UPDATE").containsEntry("actor_id", company.adminId())
                .containsEntry("before_status", "MISSING_CHECKOUT").containsEntry("after_status", "CHECKED_OUT");
    }

    @Test
    void 충돌한_날을_휴가로_정정하면_휴가에_연결하고_출퇴근_기록을_비운다() throws Exception {
        long leaveId = approvedLeave(MONDAY, MONDAY, "2030-03-01 10:00");
        long id = attendance(MONDAY, "CHECKED_OUT", "18:00");

        correct(id, "{\"status\": \"ON_VACATION\", \"reason\": \"실수로 출근 기록\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_VACATION"))
                .andExpect(jsonPath("$.data.workType").value(nullValue()))
                .andExpect(jsonPath("$.data.checkInAt").value(nullValue()))
                .andExpect(jsonPath("$.data.checkOutAt").value(nullValue()));

        assertThat(row(id)).containsEntry("status", "ON_VACATION").containsEntry("leave_request_id", leaveId)
                .containsEntry("check_in_at", null).containsEntry("check_out_at", null)
                .containsEntry("check_out_method", null);
        corrections().andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 승인된_휴가_출장이_없는_날은_휴가_출장으로_정정할_수_없다() throws Exception {
        long id = attendance(MONDAY, "CHECKED_OUT", "18:00");

        correct(id, "{\"status\": \"ON_VACATION\", \"reason\": \"휴가였음\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        correct(id, "{\"status\": \"ON_BUSINESS_TRIP\", \"reason\": \"출장이었음\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        assertThat(row(id)).containsEntry("status", "CHECKED_OUT").containsEntry("correction_reason", null);
    }

    @Test
    void 충돌한_날에_실제로_일했으면_사유만_저장해_확인하고_목록에서_빠진다() throws Exception {
        approvedLeave(MONDAY, MONDAY, "2030-03-01 10:00");
        long id = attendance(MONDAY, "CHECKED_OUT", "18:00");
        corrections().andExpect(jsonPath("$.data", hasSize(1)));

        correct(id, "{\"reason\": \"휴가 중 긴급 출근 — 휴가 1일은 조정으로 돌려줌\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.checkOutAt").value("2030-03-04T18:00:00+09:00"));

        // 기록과 기록 방식은 그대로이고 사유만 남는다
        assertThat(row(id)).containsEntry("status", "CHECKED_OUT").containsEntry("check_in_method", "WEB")
                .containsEntry("leave_request_id", null).containsEntry("corrected_at", "2030-03-08 12:00");
        corrections().andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 정정한_뒤에_휴가가_승인되면_다시_충돌로_나온다() throws Exception {
        long id = attendance(MONDAY, "CHECKED_OUT", "18:00");
        correct(id, "{\"workType\": \"REMOTE\", \"reason\": \"재택이었음\"}").andExpect(status().isOk());

        approvedLeave(MONDAY, MONDAY, "2030-03-08 13:00");      // 정정(12:00)보다 늦게 승인

        corrections().andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("LEAVE_CONFLICT"));
    }

    @Test
    void 출장_근태를_퇴근으로_정정하면_출장_연결을_끊는다() throws Exception {
        long tripId = approvedTrip(MONDAY, MONDAY.plusDays(1));
        long id = tripRow(MONDAY.plusDays(1), tripId);

        // 일정이 줄어 둘째 날은 사무실에서 일했다. 근무 형태를 보내지 않으면 사내다
        correct(id, """
                {"status": "CHECKED_OUT", "checkInAt": "2030-03-05T09:00:00+09:00",
                 "checkOutAt": "2030-03-05T18:00:00+09:00", "reason": "출장 일정 단축"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.workType").value("OFFICE"));

        assertThat(row(id)).containsEntry("business_trip_id", null).containsEntry("check_in_at", "2030-03-05 09:00")
                .containsEntry("check_in_method", "ADMIN");
        // 승인 뒤에 정정했으니 충돌로 나오지 않는다
        corrections().andExpect(jsonPath("$.data", hasSize(0)));
        // 다시 출장으로 되돌릴 수 있다
        correct(id, "{\"status\": \"ON_BUSINESS_TRIP\", \"reason\": \"잘못 정정함\"}").andExpect(status().isOk());
        assertThat(row(id)).containsEntry("status", "ON_BUSINESS_TRIP").containsEntry("business_trip_id", tripId)
                .containsEntry("work_type", "BUSINESS_TRIP").containsEntry("check_in_at", null);
    }

    @Test
    void 출근_상태로는_오늘_기록만_정정할_수_있다() throws Exception {
        long today = attendance(TODAY, "CHECKED_OUT", "11:00");
        long past = attendance(MONDAY, "CHECKED_OUT", "18:00");

        correct(today, "{\"status\": \"CHECKED_IN\", \"reason\": \"퇴근을 잘못 누름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.checkOutAt").value(nullValue()));
        assertThat(row(today)).containsEntry("check_out_at", null).containsEntry("check_out_method", null);

        correct(past, "{\"status\": \"CHECKED_IN\", \"reason\": \"퇴근을 잘못 누름\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.status").exists());

        // 지난 날짜의 출근 상태 기록은 상태를 보내지 않아도 출근 상태로 남길 수 없다
        long stale = attendance(MONDAY.plusDays(1), "CHECKED_IN", null);
        correct(stale, "{\"reason\": \"사유만\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.status").exists());
        assertThat(row(stale)).containsEntry("status", "CHECKED_IN").containsEntry("corrected_at", null);
    }

    @Test
    void 정정_입력_검증() throws Exception {
        long id = attendance(MONDAY, "MISSING_CHECKOUT", null);

        correct(id, "{\"status\": \"CHECKED_OUT\", \"checkOutAt\": \"2030-03-04T18:00:00+09:00\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        correct(id, "{\"status\": \"MISSING_CHECKOUT\", \"reason\": \"사유\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.status").exists());
        correct(id, "{\"status\": \"CHECKED_OUT\", \"reason\": \"사유\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkOutAt").exists());
        correct(id, "{\"status\": \"CHECKED_OUT\", \"checkOutAt\": \"2030-03-04T08:00:00+09:00\", \"reason\": \"사유\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkOutAt").exists());
        correct(id, "{\"status\": \"CHECKED_OUT\", \"checkOutAt\": \"18시\", \"reason\": \"사유\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkOutAt").exists());
        correct(id, """
                {"status": "CHECKED_OUT", "checkInAt": "2030-03-05T09:00:00+09:00",
                 "checkOutAt": "2030-03-05T18:00:00+09:00", "reason": "사유"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkInAt").exists());
        correct(id, """
                {"status": "CHECKED_OUT", "checkOutAt": "2030-03-04T18:00:00+09:00", "workType": "BUSINESS_TRIP",
                 "reason": "사유"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.workType").exists());
        // 상태를 바꾸지 않고 퇴근 시각만 넣을 수는 없다
        correct(id, "{\"checkOutAt\": \"2030-03-04T18:00:00+09:00\", \"reason\": \"사유\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.checkOutAt").exists());
        correct(id, "{\"status\": \"DONE\", \"reason\": \"사유\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
        correct(id, "{\"employeeId\": 1, \"reason\": \"사유\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.employeeId").exists());

        assertThat(row(id)).containsEntry("status", "MISSING_CHECKOUT").containsEntry("correction_reason", null);
    }

    @Test
    void 이미_정산된_월의_정정은_되지만_경고를_돌려준다() throws Exception {
        long id = attendance(MONDAY, "MISSING_CHECKOUT", null);
        jdbc.update("""
                INSERT INTO payroll_run (company_id, pay_month, pay_date, company_name_snap, ceo_name_snap,
                                         business_reg_no_snap, company_address_snap, confirmed_by)
                VALUES (?, '2030-03', DATE '2030-03-25', '회사', '대표', '000-00-00000', '서울', ?)
                """, company.id(), company.adminId());

        correct(id, "{\"status\": \"CHECKED_OUT\", \"checkOutAt\": \"2030-03-04T18:00:00+09:00\", \"reason\": \"사유\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.warning").value("PAY_MONTH_SETTLED"));
        assertThat(row(id)).containsEntry("status", "CHECKED_OUT");
    }

    // ---------------------------------------------------------------- 기록 없는 날에 근태 만들기

    @Test
    void 기록이_없는_날에_근태를_만든다() throws Exception {
        create("""
                {"employeeId": %d, "workDate": "2030-03-05", "status": "CHECKED_OUT", "workType": "FIELD",
                 "checkInAt": "2030-03-05T09:05:00+09:00", "checkOutAt": "2030-03-05T18:00:00+09:00",
                 "reason": "외근 중 출퇴근을 찍지 못함"}""".formatted(employee.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.workDate").value("2030-03-05"))
                .andExpect(jsonPath("$.data.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.workType").value("FIELD"))
                .andExpect(jsonPath("$.data.checkInAt").value("2030-03-05T09:05:00+09:00"))
                .andExpect(jsonPath("$.data.correctionReason").value("외근 중 출퇴근을 찍지 못함"));

        Long id = jdbc.queryForObject("SELECT id FROM attendance WHERE company_id = ? AND employee_id = ? AND work_date = ?",
                Long.class, company.id(), employee.id(), Date.valueOf(MONDAY.plusDays(1)));
        assertThat(row(id)).containsEntry("check_in_method", "ADMIN").containsEntry("check_out_method", "ADMIN")
                .containsEntry("corrected_by", company.adminId());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE company_id = ? AND target_type = 'ATTENDANCE' AND target_id = ? AND action = 'CREATE'
                """, Long.class, company.id(), id)).isEqualTo(1);
    }

    @Test
    void 근태_생성_거부() throws Exception {
        attendance(MONDAY, "CHECKED_OUT", "18:00");
        String body = """
                {"employeeId": %d, "workDate": "%s", "status": "%s", "checkInAt": "%sT09:00:00+09:00",
                 "checkOutAt": "%sT18:00:00+09:00", "reason": "사유"}""";

        // 그날 근태가 이미 있다
        create(body.formatted(employee.id(), "2030-03-04", "CHECKED_OUT", "2030-03-04", "2030-03-04"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        // 미래 · 입사일 이전(TestFixture 직원의 입사일은 2024-03-01)
        create(body.formatted(employee.id(), "2030-03-11", "CHECKED_OUT", "2030-03-11", "2030-03-11"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.workDate").exists());
        create(body.formatted(employee.id(), "2024-02-29", "CHECKED_OUT", "2024-02-29", "2024-02-29"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.workDate").exists());
        // 상태 규칙은 정정과 같다
        create(body.formatted(employee.id(), "2030-03-05", "MISSING_CHECKOUT", "2030-03-05", "2030-03-05"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.status").exists());
        create("{\"employeeId\": %d, \"workDate\": \"2030-03-05\", \"status\": \"ON_VACATION\", \"reason\": \"사유\"}"
                .formatted(employee.id()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        create("{\"employeeId\": %d, \"workDate\": \"2030-03-05\", \"status\": \"CHECKED_OUT\"}".formatted(employee.id()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
    }

    // ---------------------------------------------------------------- 권한 · 회사 격리

    @Test
    void 정정은_ATTENDANCE_MANAGE_가_있어야_한다() throws Exception {
        long id = attendance(MONDAY, "MISSING_CHECKOUT", null);

        as(employee.id(), get("/api/attendances/corrections")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        as(employee.id(), patch("/api/attendances/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"사유\"}")).andExpect(status().isForbidden());
        as(employee.id(), post("/api/attendances").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_회사의_근태와_직원은_404이고_목록에_섞이지_않는다() throws Exception {
        long id = attendance(MONDAY, "MISSING_CHECKOUT", null);
        TestFixture.Company other = fixture.company("다른회사");
        String otherAdmin = fixture.token(other.adminId(), other.id());

        mvc.perform(get("/api/attendances/corrections").header("Authorization", otherAdmin))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(patch("/api/attendances/" + id).header("Authorization", otherAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"사유\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(post("/api/attendances").header("Authorization", otherAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"employeeId": %d, "workDate": "2030-03-05", "status": "CHECKED_OUT",
                                 "checkInAt": "2030-03-05T09:00:00+09:00", "checkOutAt": "2030-03-05T18:00:00+09:00",
                                 "reason": "사유"}""".formatted(employee.id())))
                .andExpect(status().isNotFound());
        assertThat(correctionService.countCorrections(other.id())).isZero();
        assertThat(row(id)).containsEntry("correction_reason", null);
    }
}
