package com.nexuslabs.hr.domain.attendance;

import com.nexuslabs.hr.support.TestClock;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-03 근태 조회 — 내 근태 · 직원별 월 합계 · 한 직원의 근태(API 설계서 7.1). 계산 자체는 AttendanceCalculatorTest 가 본다.
 * 결근 · 미기록이 "오늘"에 따라 갈리므로 테스트용 시계를 쓴다. JDBC 만 쓰는 조회라 테스트마다 롤백한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
@Transactional
class AttendanceQueryTest {

    /** 2030-03-04(월). 오늘은 그 주 금요일(3/8)로 둔다. */
    static final LocalDate MONDAY = TestClock.MONDAY;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    long team;
    TestFixture.Employee lead, member, outsider;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("근태조회테스트");
        team = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "개발팀");
        lead = fixture.employee(company.id(), team, "직원", false);
        member = fixture.employee(company.id(), team, "직원", false);
        outsider = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        fixture.lead(company.id(), team, lead.id());
        // 직원 등록일(결근 판정 시작일)을 3/4 로 맞춘다 — 그 전 근무일은 결근이 아니다
        jdbc.update("UPDATE employee SET created_at = TIMESTAMPTZ '2030-03-04 08:00+09' WHERE company_id = ?",
                company.id());
        clock.set(MONDAY.plusDays(4).atTime(12, 0));
    }

    private ResultActions as(long employeeId, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", fixture.token(employeeId, company.id())));
    }

    private ResultActions asAdmin(String url) throws Exception {
        return as(company.adminId(), url);
    }

    private void worked(long employeeId, LocalDate date, String workType, String in, String out) {
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_out_at,
                                        check_in_method, check_out_method)
                VALUES (?, ?, ?, 'CHECKED_OUT', ?::work_type, (?::date + ?::time) AT TIME ZONE 'Asia/Seoul',
                        (?::date + ?::time) AT TIME ZONE 'Asia/Seoul', 'WEB', 'WEB')
                """, company.id(), employeeId, Date.valueOf(date), workType, Date.valueOf(date), in, Date.valueOf(date), out);
    }

    private void vacation(long employeeId, LocalDate date) {
        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, company.id());
        long leaveId = jdbc.queryForObject("""
                INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date,
                                           days, status)
                VALUES (?, ?, ?, ?, ?, ?, 1, 'APPROVED') RETURNING id
                """, Long.class, company.id(), employeeId, annual, date.getYear(), Date.valueOf(date), Date.valueOf(date));
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, check_in_method, leave_request_id)
                VALUES (?, ?, ?, 'ON_VACATION', 'SYSTEM', ?)
                """, company.id(), employeeId, Date.valueOf(date), leaveId);
    }

    private void approvedOvertime(long employeeId, LocalDate date, int requested, int approved) {
        jdbc.update("""
                INSERT INTO overtime_request (company_id, employee_id, work_date, planned_start, planned_end,
                                              requested_minutes, approved_minutes, reason, status)
                VALUES (?, ?, ?, (?::date + time '18:00') AT TIME ZONE 'Asia/Seoul',
                        (?::date + time '23:00') AT TIME ZONE 'Asia/Seoul', ?, ?, '배포', 'APPROVED')
                """, company.id(), employeeId, Date.valueOf(date), Date.valueOf(date), Date.valueOf(date), requested,
                approved);
    }

    private void statusChange(long employeeId, String status, LocalDate effectiveDate) {
        jdbc.update("""
                INSERT INTO employment_status_history (company_id, employee_id, status, effective_date, reason)
                VALUES (?, ?, ?::emp_status, ?, '테스트')
                """, company.id(), employeeId, status, Date.valueOf(effectiveDate));
    }

    /** member 의 한 주: 월 정상+연장 · 화 기록 없음 · 수 휴가 · 목 재택 지각 · 금(오늘) 기록 없음. */
    private void memberWeek() {
        worked(member.id(), MONDAY, "OFFICE", "08:55", "22:40");
        approvedOvertime(member.id(), MONDAY, 120, 90);
        vacation(member.id(), MONDAY.plusDays(2));
        worked(member.id(), MONDAY.plusDays(3), "REMOTE", "09:12", "18:05");
    }

    // ---------------------------------------------------------------- 내 근태

    @Test
    void 내_근태는_그_달의_모든_날짜와_월_합계를_준다() throws Exception {
        memberWeek();

        as(member.id(), "/api/me/attendances?month=2030-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2030-03"))
                .andExpect(jsonPath("$.data.days", hasSize(31)))
                // 3/1(금) — 등록일 전이라 결근이 아니다
                .andExpect(jsonPath("$.data.days[0].date").value("2030-03-01"))
                .andExpect(jsonPath("$.data.days[0].isWorkday").value(true))
                .andExpect(jsonPath("$.data.days[0].status").value(nullValue()))
                // 3/2(토)
                .andExpect(jsonPath("$.data.days[1].isWorkday").value(false))
                .andExpect(jsonPath("$.data.days[1].status").value(nullValue()))
                // 3/4(월) — 연장은 승인된 90분까지, 야간 40분
                .andExpect(jsonPath("$.data.days[3].status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.days[3].workType").value("OFFICE"))
                .andExpect(jsonPath("$.data.days[3].checkInAt").value("2030-03-04T08:55:00+09:00"))
                .andExpect(jsonPath("$.data.days[3].checkOutAt").value("2030-03-04T22:40:00+09:00"))
                .andExpect(jsonPath("$.data.days[3].late").value(false))
                .andExpect(jsonPath("$.data.days[3].workMinutes").value(765))
                .andExpect(jsonPath("$.data.days[3].overtimeMinutes").value(90))
                .andExpect(jsonPath("$.data.days[3].nightMinutes").value(40))
                .andExpect(jsonPath("$.data.days[3].approvedOvertimeMinutes").value(90))
                // 3/5(화) 결근 — 필드는 빠지지 않고 null
                .andExpect(jsonPath("$.data.days[4].status").value("ABSENT"))
                .andExpect(jsonPath("$.data.days[4].workMinutes").value(nullValue()))
                .andExpect(jsonPath("$.data.days[4].late").value(nullValue()))
                .andExpect(jsonPath("$.data.days[4].holidayName").value(nullValue()))
                // 3/6(수) 휴가, 3/7(목) 재택 지각, 3/8(금, 오늘) 미기록
                .andExpect(jsonPath("$.data.days[5].status").value("ON_VACATION"))
                .andExpect(jsonPath("$.data.days[6].late").value(true))
                .andExpect(jsonPath("$.data.days[6].workMinutes").value(473))
                .andExpect(jsonPath("$.data.days[7].status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.data.days[10].status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.data.summary.lateCount").value(1))
                .andExpect(jsonPath("$.data.summary.earlyLeaveCount").value(0))
                .andExpect(jsonPath("$.data.summary.absentDays").value(1))
                .andExpect(jsonPath("$.data.summary.remoteDays").value(1))
                .andExpect(jsonPath("$.data.summary.fieldDays").value(0))
                .andExpect(jsonPath("$.data.summary.tripDays").value(0))
                .andExpect(jsonPath("$.data.summary.workMinutes").value(765 + 473))
                .andExpect(jsonPath("$.data.summary.overtimeMinutes").value(90))
                .andExpect(jsonPath("$.data.summary.nightMinutes").value(40))
                .andExpect(jsonPath("$.data.summary.holidayMinutes").value(0))
                .andExpect(jsonPath("$.data.summary.holidayOvertimeMinutes").value(0));
    }

    @Test
    void 월을_빼면_이번_달이고_형식이_틀리면_입력값_오류다() throws Exception {
        as(member.id(), "/api/me/attendances")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2030-03"));
        as(member.id(), "/api/me/attendances?month=2030-02")
                .andExpect(jsonPath("$.data.days", hasSize(28)))
                .andExpect(jsonPath("$.data.summary.absentDays").value(0));
        as(member.id(), "/api/me/attendances?month=202603")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 휴일은_이름과_함께_근무일이_아닌_날로_나온다() throws Exception {
        jdbc.update("""
                INSERT INTO holiday (company_id, holiday_date, name, holiday_type, is_recurring)
                VALUES (?, DATE '2030-03-05', '창립기념일', 'COMPANY', FALSE)
                """, company.id());

        as(member.id(), "/api/me/attendances?month=2030-03")
                .andExpect(jsonPath("$.data.days[4].isWorkday").value(false))
                .andExpect(jsonPath("$.data.days[4].holidayName").value("창립기념일"))
                .andExpect(jsonPath("$.data.days[4].status").value(nullValue()))
                // 기록이 없는 3/4 · 3/6 · 3/7 만 결근이고 휴일인 3/5 는 아니다
                .andExpect(jsonPath("$.data.summary.absentDays").value(3));
    }

    @Test
    void 휴직_기간은_휴직으로_퇴직_발효일부터는_상태가_없다() throws Exception {
        statusChange(member.id(), "ON_LEAVE", MONDAY.plusDays(1));     // 3/5 부터 휴직
        statusChange(member.id(), "RESIGNED", MONDAY.plusDays(3));     // 3/7 부터 퇴직(3/6 이 마지막 재직일)

        as(company.adminId(), "/api/attendances/employees/" + member.id() + "?month=2030-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[3].status").value("ABSENT"))        // 3/4 재직중, 기록 없음
                .andExpect(jsonPath("$.data.days[4].status").value("ON_LEAVE"))      // 3/5
                .andExpect(jsonPath("$.data.days[5].status").value("ON_LEAVE"))      // 3/6
                .andExpect(jsonPath("$.data.days[6].status").value(nullValue()))     // 3/7 퇴직
                .andExpect(jsonPath("$.data.summary.absentDays").value(1));
    }

    // ---------------------------------------------------------------- 직원별 월 합계

    @Test
    void 전사_범위는_그_달에_재직한_직원_모두의_월_합계를_사원번호_순으로_준다() throws Exception {
        memberWeek();

        asAdmin("/api/attendances?month=2030-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[*].employeeId")
                        .value(contains((int) lead.id(), (int) member.id(), (int) outsider.id(), (int) company.adminId())))
                .andExpect(jsonPath("$.data.content[1].orgUnitName").value("개발팀"))
                .andExpect(jsonPath("$.data.content[1].summary.lateCount").value(1))
                .andExpect(jsonPath("$.data.content[1].summary.absentDays").value(1))
                .andExpect(jsonPath("$.data.content[1].summary.workMinutes").value(765 + 473))
                .andExpect(jsonPath("$.data.content[1].summary.overtimeMinutes").value(90))
                // 기록이 하나도 없는 직원은 3/4~3/7 나흘이 결근
                .andExpect(jsonPath("$.data.content[0].summary.absentDays").value(4));
    }

    @Test
    void 조직과_직원으로_좁히고_페이지를_나눈다() throws Exception {
        asAdmin("/api/attendances?month=2030-03&orgUnitId=" + team)
                .andExpect(jsonPath("$.data.content[*].employeeId").value(contains((int) lead.id(), (int) member.id())));
        asAdmin("/api/attendances?month=2030-03&employeeId=" + outsider.id())
                .andExpect(jsonPath("$.data.content[*].employeeId").value(contains((int) outsider.id())));
        asAdmin("/api/attendances?month=2030-03&size=3&page=1")
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void 그_달에_하루도_재직하지_않은_직원은_빠지고_그_달에_퇴직한_직원은_나온다() throws Exception {
        statusChange(outsider.id(), "RESIGNED", LocalDate.of(2030, 3, 1));   // 3/1 부터 퇴직 — 3월에는 재직하지 않았다
        statusChange(member.id(), "RESIGNED", MONDAY.plusDays(2));           // 3/6 부터 퇴직 — 3월에 재직했다

        asAdmin("/api/attendances?month=2030-03")
                .andExpect(jsonPath("$.data.content[*].employeeId")
                        .value(contains((int) lead.id(), (int) member.id(), (int) company.adminId())))
                .andExpect(jsonPath("$.data.content[1].summary.absentDays").value(2));     // 3/4 · 3/5
        // 입사 전인 달에는 나오지 않는다(TestFixture 직원의 입사일은 2024-03-01)
        asAdmin("/api/attendances?month=2024-02")
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    // ---------------------------------------------------------------- 권한 · 범위

    @Test
    void 팀_범위는_내가_조직장인_조직의_직원만_본다() throws Exception {
        as(lead.id(), "/api/attendances?month=2030-03")
                .andExpect(jsonPath("$.data.content[*].employeeId").value(contains((int) lead.id(), (int) member.id())));
        // 범위 밖 직원을 필터로 넣어도 나오지 않는다
        as(lead.id(), "/api/attendances?month=2030-03&employeeId=" + outsider.id())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        as(lead.id(), "/api/attendances/employees/" + member.id() + "?month=2030-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days", hasSize(31)));
        as(lead.id(), "/api/attendances/employees/" + outsider.id())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
        // 조직장이 아니면 팀 범위에 아무도 없다
        as(member.id(), "/api/attendances?month=2030-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        as(member.id(), "/api/attendances/employees/" + lead.id())
                .andExpect(status().isForbidden());
    }

    @Test
    void ATTENDANCE_READ_가_없으면_다른_직원의_근태를_볼_수_없지만_내_근태는_본다() throws Exception {
        jdbc.update("""
                DELETE FROM role_permission
                WHERE company_id = ? AND permission_code = 'ATTENDANCE_READ'
                  AND role_id = (SELECT id FROM role WHERE company_id = ? AND name = '직원')
                """, company.id(), company.id());

        as(lead.id(), "/api/attendances").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        as(lead.id(), "/api/attendances/employees/" + member.id()).andExpect(status().isForbidden());
        as(lead.id(), "/api/me/attendances").andExpect(status().isOk());
    }

    @Test
    void 다른_회사의_직원은_404이고_목록에_섞이지_않는다() throws Exception {
        TestFixture.Company other = fixture.company("다른회사");
        TestFixture.Employee stranger = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false);

        asAdmin("/api/attendances/employees/" + stranger.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        asAdmin("/api/attendances?month=2030-03&employeeId=" + stranger.id())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        asAdmin("/api/attendances?month=2030-03")
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }
}
