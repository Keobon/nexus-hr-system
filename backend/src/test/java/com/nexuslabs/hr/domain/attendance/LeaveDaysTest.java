package com.nexuslabs.hr.domain.attendance;

import com.nexuslabs.hr.domain.attendance.service.AttendanceService;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-ATT-05 휴가 연동 — 휴가 영역이 승인 · 취소 승인 트랜잭션 안에서 부르는 createLeaveDays · deleteLeaveDays.
 * 회사 등록 때 만들어지는 기본 근무시간(월–금)에 기대므로 오늘이 언제든 결과가 같도록 2020년 날짜로 확인한다.
 */
@SpringBootTest
@Transactional
class LeaveDaysTest {

    /** 2020-01-06 은 월요일이다. 회사의 첫 근무시간보다 이른 날짜라 첫 근무시간(월–금)이 적용된다. */
    static final LocalDate MONDAY = LocalDate.of(2020, 1, 6);

    @Autowired AttendanceService attendanceService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    TestFixture.Employee employee;
    long annualType;

    @BeforeEach
    void setUp() {
        company = fixture.company("휴가근태테스트");
        employee = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        annualType = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, company.id());
    }

    private long leave(long companyId, long employeeId, long leaveTypeId, LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                        INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date,
                                                   end_date, days, status)
                        VALUES (?, ?, ?, ?, ?, ?, 1, 'APPROVED') RETURNING id
                        """,
                Long.class, companyId, employeeId, leaveTypeId, start.getYear(), Date.valueOf(start), Date.valueOf(end));
    }

    private long leave(LocalDate start, LocalDate end) {
        return leave(company.id(), employee.id(), annualType, start, end);
    }

    private void checkIn(LocalDate date) {
        jdbc.update("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_in_method)
                VALUES (?, ?, ?, 'CHECKED_IN', 'OFFICE', ?::date + time '09:00', 'WEB')
                """, company.id(), employee.id(), Date.valueOf(date), Date.valueOf(date));
    }

    private List<Map<String, Object>> attendances() {
        return jdbc.queryForList("""
                SELECT work_date, status::text AS status, work_type::text AS work_type, check_in_at,
                       check_in_method::text AS check_in_method, leave_request_id
                FROM attendance WHERE company_id = ? AND employee_id = ? ORDER BY work_date
                """, company.id(), employee.id());
    }

    private List<LocalDate> dates() {
        return attendances().stream().map(a -> ((Date) a.get("work_date")).toLocalDate()).toList();
    }

    @Test
    void 휴가_기간의_근무일마다_휴가_근태가_생긴다() {
        assertThat(MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        // 금요일부터 다음 주 화요일까지 — 토·일은 근무일이 아니다
        long leaveId = leave(MONDAY.plusDays(4), MONDAY.plusDays(8));

        attendanceService.createLeaveDays(company.id(), leaveId);

        assertThat(dates()).containsExactly(MONDAY.plusDays(4), MONDAY.plusDays(7), MONDAY.plusDays(8));
        assertThat(attendances()).allSatisfy(a -> {
            assertThat(a.get("status")).isEqualTo("ON_VACATION");
            assertThat(a.get("leave_request_id")).isEqualTo(leaveId);
            assertThat(a.get("check_in_method")).isEqualTo("SYSTEM");
            assertThat(a.get("work_type")).isNull();
            assertThat(a.get("check_in_at")).isNull();
        });
    }

    @Test
    void 휴일은_건너뛴다() {
        jdbc.update("""
                INSERT INTO holiday (company_id, holiday_date, name, holiday_type, is_recurring)
                VALUES (?, ?, '휴일', 'COMPANY', FALSE)
                """, company.id(), Date.valueOf(MONDAY.plusDays(1)));
        long leaveId = leave(MONDAY, MONDAY.plusDays(2));

        attendanceService.createLeaveDays(company.id(), leaveId);

        assertThat(dates()).containsExactly(MONDAY, MONDAY.plusDays(2));
    }

    @Test
    void 이미_출근한_날은_덮어쓰지_않고_건너뛴다() {
        checkIn(MONDAY.plusDays(1));
        long leaveId = leave(MONDAY, MONDAY.plusDays(2));

        attendanceService.createLeaveDays(company.id(), leaveId);

        assertThat(attendances()).extracting(a -> a.get("status"))
                .containsExactly("ON_VACATION", "CHECKED_IN", "ON_VACATION");
        assertThat(attendances().get(1).get("leave_request_id")).isNull();
    }

    @Test
    void 취소하면_그_휴가로_만든_휴가_근태만_지운다() {
        checkIn(MONDAY.plusDays(1));
        long cancelled = leave(MONDAY, MONDAY.plusDays(2));
        long kept = leave(MONDAY.plusDays(3), MONDAY.plusDays(3));
        attendanceService.createLeaveDays(company.id(), cancelled);
        attendanceService.createLeaveDays(company.id(), kept);

        attendanceService.deleteLeaveDays(company.id(), cancelled);

        // 건너뛴 날의 출근 기록과 다른 휴가의 근태는 남는다
        assertThat(dates()).containsExactly(MONDAY.plusDays(1), MONDAY.plusDays(3));
        assertThat(attendances().get(1).get("leave_request_id")).isEqualTo(kept);
    }

    @Test
    void 다른_회사의_휴가는_찾지_못하고_지우지도_않는다() {
        long leaveId = leave(MONDAY, MONDAY);
        attendanceService.createLeaveDays(company.id(), leaveId);
        TestFixture.Company other = fixture.company("다른회사");

        assertThatThrownBy(() -> attendanceService.createLeaveDays(other.id(), leaveId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        attendanceService.deleteLeaveDays(other.id(), leaveId);

        assertThat(dates()).containsExactly(MONDAY);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_밖에서는_부를_수_없다() {
        assertThatThrownBy(() -> attendanceService.createLeaveDays(company.id(), 1L))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> attendanceService.deleteLeaveDays(company.id(), 1L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
