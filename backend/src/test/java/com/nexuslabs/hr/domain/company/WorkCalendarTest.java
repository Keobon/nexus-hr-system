package com.nexuslabs.hr.domain.company;

import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.domain.company.service.WorkScheduleView;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 근무일 판정(BR-WORK-001, BR-LEAVE-008) — 휴가 일수 · 근태 · 정산이 같이 쓰는 WorkCalendar.
 * 회사 등록 때 만들어지는 기본 근무시간(월–금, 적용 시작일 = 오늘)에 기대므로, 오늘이 언제든 결과가 같도록
 * 먼 과거(2020년)와 먼 미래(2999년) 날짜로 확인한다. JDBC 만 쓰지만 새 회사가 필요해 지우지 않는 방식으로 돈다.
 */
@SpringBootTest
class WorkCalendarTest {

    /** 2020-01-06 은 월요일이다. 회사의 첫 근무시간보다 이른 날짜라 첫 근무시간(월–금)이 적용된다. */
    static final LocalDate PAST_MONDAY = LocalDate.of(2020, 1, 6);
    static final LocalDate FUTURE_MONDAY = LocalDate.of(2999, 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    @Autowired WorkCalendar workCalendar;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    long companyId;

    @BeforeEach
    void setUp() {
        companyId = fixture.company("근무일테스트").id();
    }

    private void holiday(LocalDate date, boolean recurring) {
        jdbc.update("""
                INSERT INTO holiday (company_id, holiday_date, name, holiday_type, is_recurring)
                VALUES (?, ?, ?, ?::holiday_type, ?)
                """, companyId, Date.valueOf(date), "휴일", "COMPANY", recurring);
    }

    /** 월–토(비트값 63) 근무, 08:00–17:00. */
    private void sixDaySchedule(LocalDate effectiveFrom) {
        jdbc.update("""
                INSERT INTO work_schedule (company_id, effective_from, start_time, end_time, break_minutes,
                                           late_grace_minutes, overtime_approval_required, work_days)
                VALUES (?, ?, ?::time, ?::time, 30, 10, FALSE, 63)
                """, companyId, Date.valueOf(effectiveFrom), "08:00", "17:00");
    }

    @Test
    void 근무_요일이면서_휴일이_아닌_날이_근무일이다() {
        assertThat(PAST_MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(workCalendar.isWorkday(companyId, PAST_MONDAY)).isTrue();
        assertThat(workCalendar.isWorkday(companyId, PAST_MONDAY.plusDays(4))).isTrue();    // 금
        assertThat(workCalendar.isWorkday(companyId, PAST_MONDAY.plusDays(5))).isFalse();   // 토
        assertThat(workCalendar.isWorkday(companyId, PAST_MONDAY.plusDays(6))).isFalse();   // 일

        holiday(PAST_MONDAY.plusDays(2), false);
        assertThat(workCalendar.isWorkday(companyId, PAST_MONDAY.plusDays(2))).isFalse();
        assertThat(workCalendar.isWorkday(companyId, PAST_MONDAY.plusDays(1))).isTrue();
    }

    @Test
    void 근무일_수는_양끝을_포함해_센다() {
        LocalDate sunday = PAST_MONDAY.plusDays(6);

        assertThat(workCalendar.workdaysBetween(companyId, PAST_MONDAY, sunday)).isEqualTo(5);
        assertThat(workCalendar.workdaysBetween(companyId, PAST_MONDAY, PAST_MONDAY)).isEqualTo(1);
        assertThat(workCalendar.workdaysBetween(companyId, sunday.minusDays(1), sunday)).isZero();
        assertThat(workCalendar.workdaysBetween(companyId, sunday, PAST_MONDAY)).isZero();
        assertThat(workCalendar.workdaysBetween(companyId, PAST_MONDAY, PAST_MONDAY.plusDays(13))).isEqualTo(10);

        holiday(PAST_MONDAY.plusDays(3), false);
        holiday(PAST_MONDAY.plusDays(5), false);   // 토요일 휴일은 원래 근무일이 아니라 영향이 없다
        assertThat(workCalendar.workdaysBetween(companyId, PAST_MONDAY, sunday)).isEqualTo(4);
    }

    @Test
    void 매년_반복_휴일은_등록한_해부터_적용한다() {
        LocalDate registered = FUTURE_MONDAY;                      // 2999년의 한 월요일에 등록
        holiday(registered, true);
        LocalDate nextYear = registered.plusYears(1);
        LocalDate lastYear = registered.minusYears(1);

        assertThat(workCalendar.isWorkday(companyId, registered)).isFalse();
        assertThat(workCalendar.isWorkday(companyId, nextYear)).isFalse();
        // 등록한 해보다 이전 해의 같은 월·일은 휴일이 아니다 — 요일만 본다
        assertThat(workCalendar.isWorkday(companyId, lastYear))
                .isEqualTo(lastYear.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue());
        assertThat(workCalendar.snapshot(companyId).holidayName(nextYear)).isEqualTo("휴일");
        assertThat(workCalendar.snapshot(companyId).holidayName(nextYear.plusDays(1))).isNull();
    }

    @Test
    void 근무시간이_바뀌면_적용_시작일부터_새_근무_요일로_판정한다() {
        LocalDate changeDate = FUTURE_MONDAY;
        sixDaySchedule(changeDate);
        LocalDate saturdayBefore = changeDate.minusDays(2);
        LocalDate saturdayAfter = changeDate.plusDays(5);

        assertThat(saturdayBefore.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(workCalendar.isWorkday(companyId, saturdayBefore)).isFalse();
        assertThat(workCalendar.isWorkday(companyId, saturdayAfter)).isTrue();
        assertThat(workCalendar.isWorkday(companyId, saturdayAfter.plusDays(1))).isFalse();   // 일
        // 바뀌는 주를 걸친 기간: 앞 주 월–금 5일 + 뒤 주 월–토 6일
        assertThat(workCalendar.workdaysBetween(companyId, changeDate.minusDays(7), changeDate.plusDays(6)))
                .isEqualTo(11);
    }

    @Test
    void 그날의_근무시간_기준을_돌려준다() {
        sixDaySchedule(FUTURE_MONDAY);

        WorkScheduleView first = workCalendar.scheduleOn(companyId, PAST_MONDAY);
        assertThat(first.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(first.endTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(first.dailyStandardMinutes()).isEqualTo(480);
        assertThat(first.overtimeApprovalRequired()).isTrue();
        assertThat(first.workDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

        WorkScheduleView before = workCalendar.scheduleOn(companyId, FUTURE_MONDAY.minusDays(1));
        assertThat(before.startTime()).isEqualTo(LocalTime.of(9, 0));

        WorkScheduleView changed = workCalendar.scheduleOn(companyId, FUTURE_MONDAY);
        assertThat(changed.effectiveFrom()).isEqualTo(FUTURE_MONDAY);
        assertThat(changed.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(changed.breakMinutes()).isEqualTo(30);
        assertThat(changed.lateGraceMinutes()).isEqualTo(10);
        assertThat(changed.dailyStandardMinutes()).isEqualTo(510);
        assertThat(changed.overtimeApprovalRequired()).isFalse();
        assertThat(changed.workDays()).contains(DayOfWeek.SATURDAY).doesNotContain(DayOfWeek.SUNDAY);
    }

    @Test
    void 다른_회사의_휴일과_근무시간은_영향을_주지_않는다() {
        long otherCompanyId = fixture.company("근무일테스트타사").id();
        holiday(PAST_MONDAY, false);
        sixDaySchedule(FUTURE_MONDAY);

        assertThat(workCalendar.isWorkday(companyId, PAST_MONDAY)).isFalse();
        assertThat(workCalendar.isWorkday(otherCompanyId, PAST_MONDAY)).isTrue();
        assertThat(workCalendar.isWorkday(otherCompanyId, FUTURE_MONDAY.plusDays(5))).isFalse();
    }
}
