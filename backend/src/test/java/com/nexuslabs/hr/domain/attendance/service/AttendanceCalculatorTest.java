package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceDay;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceSummary;
import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.attendance.service.AttendanceCalculator.DayInput;
import com.nexuslabs.hr.domain.attendance.service.AttendanceCalculator.Employment;
import com.nexuslabs.hr.domain.company.service.WorkScheduleView;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 근태 계산(F-ATT-03, BR-WORK-002) — DB 없이 계산만. 앞의 세 건은 백엔드 개발 안내 v2 6.3 "근태" 예제의 숫자 그대로다.
 * 계산 규칙이 바뀌면 기능명세서를 먼저 고치고 여기 기대값을 다시 계산한다.
 */
class AttendanceCalculatorTest {

    /** 2026-10-01 은 목요일(근무일), 2026-10-03 은 토요일이다. */
    static final LocalDate THURSDAY = LocalDate.of(2026, 10, 1);
    static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    static final ZoneOffset KST = ZoneOffset.ofHours(9);

    /** 09:00–18:00, 휴게 60분, 지각 허용 0분, 야간 22:00–06:00, 연장근무 승인 필요. */
    static final WorkScheduleView APPROVAL_REQUIRED = schedule(0, true);
    static final WorkScheduleView NO_APPROVAL = schedule(0, false);

    private static WorkScheduleView schedule(int lateGraceMinutes, boolean approvalRequired) {
        return new WorkScheduleView(LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), LocalTime.of(18, 0), 60,
                lateGraceMinutes, LocalTime.of(22, 0), LocalTime.of(6, 0), approvalRequired,
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
    }

    private static OffsetDateTime at(LocalDate date, String time) {
        return LocalDateTime.of(date, LocalTime.parse(time)).atOffset(KST);
    }

    /** 근무일에 출근 · 퇴근한 하루. */
    private static AttendanceDay worked(WorkScheduleView schedule, String in, OffsetDateTime out, Integer approved) {
        return AttendanceCalculator.day(new DayInput(THURSDAY, true, null, schedule, AttendanceStatus.CHECKED_OUT,
                WorkType.OFFICE, at(THURSDAY, in), out, approved, Employment.ACTIVE, TODAY));
    }

    private static AttendanceDay worked(WorkScheduleView schedule, String in, String out, Integer approved) {
        return worked(schedule, in, at(THURSDAY, out), approved);
    }

    /** 근무일이 아닌 날(토요일)에 출근 · 퇴근한 하루. */
    private static AttendanceDay holidayWork(WorkScheduleView schedule, String in, String out, Integer approved) {
        return AttendanceCalculator.day(new DayInput(SATURDAY, false, null, schedule, AttendanceStatus.CHECKED_OUT,
                WorkType.OFFICE, at(SATURDAY, in), at(SATURDAY, out), approved, Employment.ACTIVE, TODAY));
    }

    private static AttendanceDay noRecord(LocalDate date, boolean workday, Employment employment) {
        return AttendanceCalculator.day(new DayInput(date, workday, null, APPROVAL_REQUIRED, null, null, null, null,
                null, employment, TODAY));
    }

    // ---------------------------------------------------------------- 백엔드 안내 6.3 예제

    @Test
    void 예제1_연장은_승인된_시간까지만_야간은_기록대로() {
        AttendanceDay day = worked(APPROVAL_REQUIRED, "09:00", "23:00", 180);

        assertThat(day.workMinutes()).isEqualTo(780);
        assertThat(day.overtimeMinutes()).isEqualTo(180);       // min(300, 180)
        assertThat(day.nightMinutes()).isEqualTo(60);           // 22–23시
        assertThat(day.late()).isFalse();
        assertThat(day.earlyLeave()).isFalse();
        assertThat(day.approvedOvertimeMinutes()).isEqualTo(180);
        assertThat(day.status()).isEqualTo(AttendanceDayStatus.CHECKED_OUT);
    }

    @Test
    void 예제2_지각() {
        AttendanceDay day = worked(APPROVAL_REQUIRED, "09:12", "18:05", null);

        assertThat(day.workMinutes()).isEqualTo(473);
        assertThat(day.overtimeMinutes()).isZero();
        assertThat(day.nightMinutes()).isZero();
        assertThat(day.late()).isTrue();
        assertThat(day.earlyLeave()).isFalse();
        assertThat(day.approvedOvertimeMinutes()).isZero();
    }

    @Test
    void 예제3_조퇴() {
        AttendanceDay day = worked(APPROVAL_REQUIRED, "09:00", "17:30", null);

        assertThat(day.workMinutes()).isEqualTo(450);
        assertThat(day.overtimeMinutes()).isZero();
        assertThat(day.late()).isFalse();
        assertThat(day.earlyLeave()).isTrue();
    }

    @Test
    void 데모_데이터_임태현_10월_1일() {
        // 08:55 출근 22:40 퇴근, 연장 2시간 신청 → 1시간 30분 승인 (schema.sql v2 5.2, API 7.1 예시)
        AttendanceDay day = worked(APPROVAL_REQUIRED, "08:55", "22:40", 90);

        assertThat(day.workMinutes()).isEqualTo(765);
        assertThat(day.overtimeMinutes()).isEqualTo(90);
        assertThat(day.nightMinutes()).isEqualTo(40);
    }

    // ---------------------------------------------------------------- 지각 · 조퇴

    @Test
    void 초는_버리고_지각_허용_분까지는_지각이_아니다() {
        assertThat(worked(APPROVAL_REQUIRED, "09:00:59", "18:00", null).late()).isFalse();
        assertThat(worked(APPROVAL_REQUIRED, "09:01:00", "18:00", null).late()).isTrue();

        WorkScheduleView grace = schedule(10, true);
        assertThat(worked(grace, "09:10:30", "18:00", null).late()).isFalse();
        assertThat(worked(grace, "09:11", "18:00", null).late()).isTrue();
        // 근무시간도 분 단위로 내린 시각으로 계산한다
        assertThat(worked(APPROVAL_REQUIRED, "09:00:59", "18:00:59", null).workMinutes()).isEqualTo(480);
    }

    @Test
    void 근무시간은_0보다_작아지지_않는다() {
        AttendanceDay day = worked(APPROVAL_REQUIRED, "09:00", "09:30", null);

        assertThat(day.workMinutes()).isZero();     // 30분 − 휴게 60분
        assertThat(day.earlyLeave()).isTrue();
    }

    // ---------------------------------------------------------------- 연장 · 야간

    @Test
    void 승인이_필요_없는_회사는_기록대로_연장을_센다() {
        assertThat(worked(NO_APPROVAL, "09:00", "23:00", null).overtimeMinutes()).isEqualTo(300);
        // 승인이 필요한 회사는 승인이 없으면 0, 승인이 더 많아도 실제 초과분까지만
        assertThat(worked(APPROVAL_REQUIRED, "09:00", "23:00", null).overtimeMinutes()).isZero();
        assertThat(worked(APPROVAL_REQUIRED, "09:00", "20:00", 300).overtimeMinutes()).isEqualTo(120);
    }

    @Test
    void 자정을_넘긴_퇴근은_그날_근무로_계산하고_야간은_자정_뒤도_센다() {
        AttendanceDay day = worked(NO_APPROVAL, "18:00", at(THURSDAY.plusDays(1), "02:00"), null);

        assertThat(day.workMinutes()).isEqualTo(420);       // 8시간 − 휴게 60분
        assertThat(day.nightMinutes()).isEqualTo(240);      // 22:00–02:00
        assertThat(day.overtimeMinutes()).isZero();
        assertThat(day.earlyLeave()).isFalse();
        assertThat(day.late()).isTrue();
    }

    @Test
    void 새벽에_출근하면_야간_끝까지가_야간이다() {
        AttendanceDay day = worked(NO_APPROVAL, "05:00", "14:00", null);

        assertThat(day.nightMinutes()).isEqualTo(60);       // 05:00–06:00
        assertThat(day.workMinutes()).isEqualTo(480);
        assertThat(day.earlyLeave()).isTrue();
    }

    // ---------------------------------------------------------------- 휴일 근무

    @Test
    void 근무일이_아닌_날은_소정근로시간까지_휴일근무_넘으면_휴일연장이다() {
        AttendanceDay day = holidayWork(NO_APPROVAL, "09:00", "20:00", null);

        assertThat(day.workMinutes()).isEqualTo(600);
        assertThat(day.holidayMinutes()).isEqualTo(480);
        assertThat(day.holidayOvertimeMinutes()).isEqualTo(120);
        assertThat(day.overtimeMinutes()).isZero();
        // 근무일이 아니면 지각 · 조퇴를 판정하지 않는다
        assertThat(holidayWork(NO_APPROVAL, "11:00", "15:00", null).late()).isFalse();
        assertThat(holidayWork(NO_APPROVAL, "11:00", "15:00", null).earlyLeave()).isFalse();
    }

    @Test
    void 승인이_필요하면_휴일근무와_휴일연장의_합이_승인_시간을_넘지_않고_휴일연장부터_줄인다() {
        AttendanceDay within = holidayWork(APPROVAL_REQUIRED, "09:00", "20:00", 540);
        assertThat(within.holidayMinutes()).isEqualTo(480);
        assertThat(within.holidayOvertimeMinutes()).isEqualTo(60);

        AttendanceDay less = holidayWork(APPROVAL_REQUIRED, "09:00", "20:00", 300);
        assertThat(less.holidayMinutes()).isEqualTo(300);
        assertThat(less.holidayOvertimeMinutes()).isZero();

        AttendanceDay none = holidayWork(APPROVAL_REQUIRED, "09:00", "20:00", null);
        assertThat(none.holidayMinutes()).isZero();
        assertThat(none.holidayOvertimeMinutes()).isZero();
        assertThat(none.workMinutes()).isEqualTo(600);
    }

    // ---------------------------------------------------------------- 퇴근 시각이 없는 날 · 출장 · 휴가

    @Test
    void 퇴근_시각이_없으면_시간은_모두_0이고_지각만_판정한다() {
        for (AttendanceStatus status : List.of(AttendanceStatus.MISSING_CHECKOUT, AttendanceStatus.CHECKED_IN)) {
            AttendanceDay day = AttendanceCalculator.day(new DayInput(THURSDAY, true, null, APPROVAL_REQUIRED, status,
                    WorkType.REMOTE, at(THURSDAY, "09:30"), null, 60, Employment.ACTIVE, TODAY));

            assertThat(day.status()).isEqualTo(AttendanceDayStatus.valueOf(status.name()));
            assertThat(day.late()).isTrue();
            assertThat(day.earlyLeave()).isFalse();
            assertThat(day.workMinutes()).isZero();
            assertThat(day.overtimeMinutes()).isZero();
            assertThat(day.nightMinutes()).isZero();
            assertThat(day.checkOutAt()).isNull();
            assertThat(day.approvedOvertimeMinutes()).isEqualTo(60);
        }
    }

    @Test
    void 출장일은_소정근로시간을_일한_것으로_본다() {
        AttendanceDay day = AttendanceCalculator.day(new DayInput(THURSDAY, true, null, APPROVAL_REQUIRED,
                AttendanceStatus.ON_BUSINESS_TRIP, WorkType.BUSINESS_TRIP, null, null, null, Employment.ACTIVE, TODAY));

        assertThat(day.status()).isEqualTo(AttendanceDayStatus.ON_BUSINESS_TRIP);
        assertThat(day.workMinutes()).isEqualTo(480);
        assertThat(day.overtimeMinutes()).isZero();
        assertThat(day.nightMinutes()).isZero();
        assertThat(day.late()).isFalse();
        assertThat(day.earlyLeave()).isFalse();
    }

    @Test
    void 휴가일은_상태만_있고_계산값은_없다() {
        AttendanceDay day = AttendanceCalculator.day(new DayInput(THURSDAY, true, null, APPROVAL_REQUIRED,
                AttendanceStatus.ON_VACATION, null, null, null, null, Employment.ACTIVE, TODAY));

        assertThat(day.status()).isEqualTo(AttendanceDayStatus.ON_VACATION);
        assertThat(day.workMinutes()).isNull();
        assertThat(day.late()).isNull();
        assertThat(day.approvedOvertimeMinutes()).isNull();
    }

    // ---------------------------------------------------------------- 기록 없는 날

    @Test
    void 기록_없는_근무일은_지난_날이면_결근_오늘_이후면_미기록이다() {
        assertThat(noRecord(TODAY.minusDays(1), true, Employment.ACTIVE).status()).isEqualTo(AttendanceDayStatus.ABSENT);
        assertThat(noRecord(TODAY, true, Employment.ACTIVE).status()).isEqualTo(AttendanceDayStatus.NOT_RECORDED);
        assertThat(noRecord(TODAY.plusDays(1), true, Employment.ACTIVE).status())
                .isEqualTo(AttendanceDayStatus.NOT_RECORDED);
        assertThat(noRecord(TODAY.minusDays(1), true, Employment.ACTIVE).workMinutes()).isNull();
    }

    @Test
    void 휴직_중이면_휴직_판정_대상이_아니거나_근무일이_아니면_상태가_없다() {
        assertThat(noRecord(TODAY.minusDays(1), true, Employment.ON_LEAVE).status())
                .isEqualTo(AttendanceDayStatus.ON_LEAVE);
        assertThat(noRecord(TODAY.minusDays(1), true, Employment.NOT_TRACKED).status()).isNull();
        assertThat(noRecord(SATURDAY, false, Employment.ACTIVE).status()).isNull();
        assertThat(noRecord(SATURDAY, false, Employment.ON_LEAVE).status()).isNull();
    }

    // ---------------------------------------------------------------- 합계

    @Test
    void 월_합계는_횟수와_분을_더한다() {
        AttendanceDay remoteLate = AttendanceCalculator.day(new DayInput(THURSDAY, true, null, APPROVAL_REQUIRED,
                AttendanceStatus.CHECKED_OUT, WorkType.REMOTE, at(THURSDAY, "09:12"), at(THURSDAY, "18:05"), null,
                Employment.ACTIVE, TODAY));
        AttendanceDay fieldEarly = AttendanceCalculator.day(new DayInput(THURSDAY.plusDays(1), true, null,
                APPROVAL_REQUIRED, AttendanceStatus.CHECKED_OUT, WorkType.FIELD, at(THURSDAY.plusDays(1), "09:00"),
                at(THURSDAY.plusDays(1), "17:30"), null, Employment.ACTIVE, TODAY));
        AttendanceDay trip = AttendanceCalculator.day(new DayInput(THURSDAY.plusDays(4), true, null, APPROVAL_REQUIRED,
                AttendanceStatus.ON_BUSINESS_TRIP, WorkType.BUSINESS_TRIP, null, null, null, Employment.ACTIVE, TODAY));

        AttendanceSummary summary = AttendanceCalculator.summarize(List.of(
                worked(APPROVAL_REQUIRED, "08:55", "22:40", 90), remoteLate, fieldEarly,
                holidayWork(NO_APPROVAL, "09:00", "20:00", null), trip,
                noRecord(THURSDAY.plusDays(5), true, Employment.ACTIVE),
                noRecord(TODAY, true, Employment.ACTIVE)));

        assertThat(summary).isEqualTo(new AttendanceSummary(1, 1, 1, 1, 1, 1,
                765 + 473 + 450 + 600 + 480, 90, 40, 480, 120));
    }
}
