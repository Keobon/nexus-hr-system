package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.company.dto.Weekday;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 근무일 판정(BR-WORK-001, BR-LEAVE-008) — 휴가 일수 · 근태 계산 · 급여 정산이 모두 이 하나를 쓴다.
 * 근무일 = 그날 유효한 근무시간의 근무 요일이면서 휴일(매년 반복 포함)이 아닌 날.
 * JDBC 로 만든 코드도 부를 수 있게 회사 ID 를 받고 값을 돌려준다. 모든 쿼리에 company_id 조건이 있다.
 */
@Component
public class WorkCalendar {

    private final JdbcTemplate jdbc;

    public WorkCalendar(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isWorkday(long companyId, LocalDate date) {
        return snapshot(companyId).isWorkday(date);
    }

    /** from 부터 to 까지(양끝 포함)의 근무일 수. from 이 to 보다 뒤면 0. */
    public int workdaysBetween(long companyId, LocalDate from, LocalDate to) {
        Snapshot snapshot = snapshot(companyId);
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (snapshot.isWorkday(date)) {
                count++;
            }
        }
        return count;
    }

    public WorkScheduleView scheduleOn(long companyId, LocalDate date) {
        return snapshot(companyId).scheduleOn(date);
    }

    /** 여러 날짜를 이어서 볼 때(한 달 근태 등) 근무시간·휴일을 한 번만 읽어 두고 쓴다. */
    public Snapshot snapshot(long companyId) {
        List<WorkScheduleView> schedules = jdbc.query("""
                        SELECT effective_from, start_time, end_time, break_minutes, late_grace_minutes, night_start,
                               night_end, overtime_approval_required, work_days
                        FROM work_schedule WHERE company_id = ? ORDER BY effective_from
                        """,
                (rs, i) -> new WorkScheduleView(rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class),
                        rs.getInt("break_minutes"), rs.getInt("late_grace_minutes"),
                        rs.getObject("night_start", LocalTime.class), rs.getObject("night_end", LocalTime.class),
                        rs.getBoolean("overtime_approval_required"), Weekday.daysOfWeek(rs.getInt("work_days"))),
                companyId);
        if (schedules.isEmpty()) {
            throw new IllegalStateException("근무시간이 하나도 없는 회사입니다: " + companyId);
        }
        List<HolidayRule> holidays = jdbc.query(
                "SELECT holiday_date, name, is_recurring FROM holiday WHERE company_id = ? ORDER BY holiday_date",
                (rs, i) -> new HolidayRule(rs.getObject("holiday_date", LocalDate.class), rs.getString("name"),
                        rs.getBoolean("is_recurring")),
                companyId);
        return new Snapshot(schedules, holidays);
    }

    /** 한 회사의 근무시간 이력과 휴일을 읽어 둔 것. */
    public static final class Snapshot {

        private final List<WorkScheduleView> schedules;   // 적용 시작일 오름차순, 1개 이상
        private final List<HolidayRule> holidays;

        private Snapshot(List<WorkScheduleView> schedules, List<HolidayRule> holidays) {
            this.schedules = schedules;
            this.holidays = holidays;
        }

        public boolean isWorkday(LocalDate date) {
            return scheduleOn(date).workDays().contains(date.getDayOfWeek()) && holidayName(date) == null;
        }

        /**
         * 적용 시작일이 그날 이전인 근무시간 중 가장 최근 것.
         * 첫 근무시간보다 이른 날짜(회사 등록 전 날짜로 휴가를 사후 신청하는 경우 등)는 첫 근무시간을 쓴다.
         */
        public WorkScheduleView scheduleOn(LocalDate date) {
            WorkScheduleView found = schedules.get(0);
            for (WorkScheduleView schedule : schedules) {
                if (!schedule.effectiveFrom().isAfter(date)) {
                    found = schedule;
                }
            }
            return found;
        }

        /** 그날이 휴일이면 휴일 이름, 아니면 null. */
        public String holidayName(LocalDate date) {
            return holidays.stream().filter(h -> h.appliesOn(date)).map(HolidayRule::name).findFirst().orElse(null);
        }
    }

    /** 매년 반복 휴일은 등록한 해부터 해마다 같은 월·일에 적용한다(2월 29일은 윤년에만). */
    private record HolidayRule(LocalDate date, String name, boolean recurring) {

        boolean appliesOn(LocalDate target) {
            if (!recurring) {
                return date.equals(target);
            }
            return target.getYear() >= date.getYear() && target.getMonth() == date.getMonth()
                    && target.getDayOfMonth() == date.getDayOfMonth();
        }
    }
}
