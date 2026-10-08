package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceDay;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceSummary;
import com.nexuslabs.hr.domain.attendance.dto.MonthlyAttendance;
import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.domain.company.service.WorkScheduleView;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 근태 시간 계산(공통 컴포넌트 ③, F-ATT-03, BR-WORK-002). 지각 · 근무 · 연장 · 야간 · 휴일 · 결근은 저장하지 않고 여기서 계산한다 —
 * 근태 조회 · 홈 · 직원 상세 · 급여 정산이 모두 이 하나를 쓴다.
 *
 * <p>하루 계산({@link #day})과 합계({@link #summarize})는 DB 없이 값만으로 한다. {@link #month} · {@link #months} 는
 * 필요한 값을 한 번에 읽어 그 계산에 넘긴다(직원마다 쿼리하지 않는다). JDBC 라서 모든 쿼리에 company_id 조건을 넣는다.
 */
@Component
public class AttendanceCalculator {

    private final JdbcTemplate jdbc;
    private final WorkCalendar workCalendar;
    private final Clock clock;

    public AttendanceCalculator(JdbcTemplate jdbc, WorkCalendar workCalendar, Clock clock) {
        this.jdbc = jdbc;
        this.workCalendar = workCalendar;
        this.clock = clock;
    }

    /** 그 직원의 그날 근태를 따질 수 있는지. NOT_TRACKED 는 결근 판정 시작일 전이거나 퇴직한 뒤다. */
    public enum Employment { NOT_TRACKED, ACTIVE, ON_LEAVE }

    /**
     * 하루 계산에 필요한 값.
     *
     * @param schedule                그날 유효한 근무시간(BR-WORK-001)
     * @param status                  저장된 근태 상태. 근태 행이 없으면 null
     * @param approvedOvertimeMinutes 그날 승인된 연장근무 시간. 없으면 null
     * @param today                   오늘 — 기록 없는 근무일이 결근(지난 날)인지 미기록(오늘 이후)인지 가른다
     */
    public record DayInput(LocalDate date, boolean workday, String holidayName, WorkScheduleView schedule,
                           AttendanceStatus status, WorkType workType, OffsetDateTime checkInAt,
                           OffsetDateTime checkOutAt, Integer approvedOvertimeMinutes, Employment employment,
                           LocalDate today) {
    }

    /** 한 직원의 한 달. 다른 회사 직원이면 NOT_FOUND. */
    public MonthlyAttendance month(long companyId, long employeeId, YearMonth month) {
        MonthlyAttendance result = months(companyId, List.of(employeeId), month).get(employeeId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return result;
    }

    /** 여러 직원의 한 달을 쿼리 다섯 번으로 계산한다. 그 회사 직원이 아닌 ID 는 결과에 없다. */
    public Map<Long, MonthlyAttendance> months(long companyId, Collection<Long> employeeIds, YearMonth month) {
        Map<Long, MonthlyAttendance> result = new LinkedHashMap<>();
        if (employeeIds.isEmpty()) {
            return result;
        }
        Long[] ids = employeeIds.toArray(Long[]::new);
        Date first = Date.valueOf(month.atDay(1));
        Date last = Date.valueOf(month.atEndOfMonth());
        LocalDate today = LocalDate.now(clock);
        WorkCalendar.Snapshot calendar = workCalendar.snapshot(companyId);

        // 결근 판정 시작일 = max(입사일, 직원 등록일) — 시스템에 등록되기 전의 출퇴근은 알 수 없다(F-ATT-03)
        Map<Long, LocalDate> trackingStart = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT id, GREATEST(hire_date, (created_at AT TIME ZONE 'Asia/Seoul')::date) AS tracking_start
                        FROM employee WHERE company_id = ? AND id = ANY(?)
                        """,
                rs -> {
                    trackingStart.put(rs.getLong("id"), rs.getObject("tracking_start", LocalDate.class));
                },
                companyId, ids);

        // 재직상태는 "발효일부터 그 상태"다(F-EMP-04) — 그날 이전의 마지막 이력이 그날의 상태
        Map<Long, List<StatusChange>> statusChanges = new HashMap<>();
        jdbc.query("""
                        SELECT employee_id, status::text AS status, effective_date FROM employment_status_history
                        WHERE company_id = ? AND employee_id = ANY(?) AND effective_date <= ?
                        ORDER BY employee_id, effective_date, id
                        """,
                rs -> {
                    statusChanges.computeIfAbsent(rs.getLong("employee_id"), k -> new ArrayList<>())
                            .add(new StatusChange(rs.getObject("effective_date", LocalDate.class),
                                    EmpStatus.valueOf(rs.getString("status"))));
                },
                companyId, ids, last);

        Map<Long, Map<LocalDate, Row>> rows = new HashMap<>();
        jdbc.query("""
                        SELECT employee_id, work_date, status::text AS status, work_type::text AS work_type,
                               check_in_at, check_out_at
                        FROM attendance
                        WHERE company_id = ? AND employee_id = ANY(?) AND work_date BETWEEN ? AND ?
                        """,
                rs -> {
                    String workType = rs.getString("work_type");
                    rows.computeIfAbsent(rs.getLong("employee_id"), k -> new HashMap<>())
                            .put(rs.getObject("work_date", LocalDate.class),
                                    new Row(AttendanceStatus.valueOf(rs.getString("status")),
                                            workType == null ? null : WorkType.valueOf(workType),
                                            rs.getObject("check_in_at", OffsetDateTime.class),
                                            rs.getObject("check_out_at", OffsetDateTime.class)));
                },
                companyId, ids, first, last);

        Map<Long, Map<LocalDate, Integer>> approved = new HashMap<>();
        jdbc.query("""
                        SELECT employee_id, work_date, approved_minutes FROM overtime_request
                        WHERE company_id = ? AND employee_id = ANY(?) AND work_date BETWEEN ? AND ?
                          AND status = 'APPROVED'
                        """,
                rs -> {
                    approved.computeIfAbsent(rs.getLong("employee_id"), k -> new HashMap<>())
                            .put(rs.getObject("work_date", LocalDate.class), rs.getInt("approved_minutes"));
                },
                companyId, ids, first, last);

        trackingStart.forEach((employeeId, start) -> {
            List<StatusChange> changes = statusChanges.getOrDefault(employeeId, List.of());
            Map<LocalDate, Row> employeeRows = rows.getOrDefault(employeeId, Map.of());
            Map<LocalDate, Integer> employeeApproved = approved.getOrDefault(employeeId, Map.of());
            List<AttendanceDay> days = new ArrayList<>();
            for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
                Row row = employeeRows.get(date);
                days.add(day(new DayInput(date, calendar.isWorkday(date), calendar.holidayName(date),
                        calendar.scheduleOn(date), row == null ? null : row.status(),
                        row == null ? null : row.workType(), row == null ? null : row.checkInAt(),
                        row == null ? null : row.checkOutAt(), employeeApproved.get(date),
                        employmentOn(date, start, changes), today)));
            }
            result.put(employeeId, new MonthlyAttendance(month.toString(), days, summarize(days)));
        });
        return result;
    }

    /**
     * 하루 계산(F-ATT-03). 출·퇴근 시각은 초를 버리고 분 단위로 계산한다.
     * 퇴근 시각이 없는 날(출근 중 · 퇴근미기록)은 시간이 모두 0 이고 지각만 판정한다.
     */
    public static AttendanceDay day(DayInput in) {
        if (in.status() == null) {
            return noRecord(in, statusWithoutRecord(in));
        }
        if (in.status() == AttendanceStatus.ON_VACATION) {
            return noRecord(in, AttendanceDayStatus.ON_VACATION);
        }
        WorkScheduleView schedule = in.schedule();
        int standard = schedule.dailyStandardMinutes();
        if (in.status() == AttendanceStatus.ON_BUSINESS_TRIP) {
            // 출장일은 1일 소정근로시간을 일한 것으로 본다(BR-ATT-005)
            return new AttendanceDay(in.date(), in.workday(), in.holidayName(), AttendanceDayStatus.ON_BUSINESS_TRIP,
                    in.workType(), null, null, false, false, standard, 0, 0, 0, 0, 0);
        }
        LocalDateTime checkIn = minute(in.checkInAt());
        boolean late = in.workday()
                && checkIn.isAfter(in.date().atTime(schedule.startTime()).plusMinutes(schedule.lateGraceMinutes()));
        int approved = in.approvedOvertimeMinutes() == null ? 0 : in.approvedOvertimeMinutes();
        AttendanceDayStatus status = AttendanceDayStatus.valueOf(in.status().name());
        if (in.checkOutAt() == null) {
            return new AttendanceDay(in.date(), in.workday(), in.holidayName(), status, in.workType(),
                    seoul(in.checkInAt()), null, late, false, 0, 0, 0, 0, 0, approved);
        }
        LocalDateTime checkOut = minute(in.checkOutAt());
        boolean earlyLeave = in.workday() && checkOut.isBefore(in.date().atTime(schedule.endTime()));
        int work = Math.max(0, (int) ChronoUnit.MINUTES.between(checkIn, checkOut) - schedule.breakMinutes());
        int night = nightMinutes(checkIn, checkOut, schedule.nightStart(), schedule.nightEnd());
        int overtime = 0;
        int holiday = 0;
        int holidayOvertime = 0;
        if (in.workday()) {
            overtime = Math.max(0, work - standard);
            if (schedule.overtimeApprovalRequired()) {
                overtime = Math.min(overtime, approved);
            }
        } else {
            holiday = Math.min(work, standard);
            holidayOvertime = Math.max(0, work - standard);
            if (schedule.overtimeApprovalRequired()) {
                // 승인된 시간을 넘는 만큼 휴일연장부터 뺀다(BR-ATT-004)
                int excess = Math.max(0, holiday + holidayOvertime - approved);
                int fromOvertime = Math.min(holidayOvertime, excess);
                holidayOvertime -= fromOvertime;
                holiday -= excess - fromOvertime;
            }
        }
        return new AttendanceDay(in.date(), in.workday(), in.holidayName(), status, in.workType(),
                seoul(in.checkInAt()), seoul(in.checkOutAt()), late, earlyLeave, work, overtime, night, holiday,
                holidayOvertime, approved);
    }

    public static AttendanceSummary summarize(List<AttendanceDay> days) {
        int late = 0, earlyLeave = 0, absent = 0, remote = 0, field = 0, trip = 0;
        int work = 0, overtime = 0, night = 0, holiday = 0, holidayOvertime = 0;
        for (AttendanceDay d : days) {
            late += Boolean.TRUE.equals(d.late()) ? 1 : 0;
            earlyLeave += Boolean.TRUE.equals(d.earlyLeave()) ? 1 : 0;
            absent += d.status() == AttendanceDayStatus.ABSENT ? 1 : 0;
            trip += d.status() == AttendanceDayStatus.ON_BUSINESS_TRIP ? 1 : 0;
            remote += d.checkInAt() != null && d.workType() == WorkType.REMOTE ? 1 : 0;
            field += d.checkInAt() != null && d.workType() == WorkType.FIELD ? 1 : 0;
            work += zeroIfNull(d.workMinutes());
            overtime += zeroIfNull(d.overtimeMinutes());
            night += zeroIfNull(d.nightMinutes());
            holiday += zeroIfNull(d.holidayMinutes());
            holidayOvertime += zeroIfNull(d.holidayOvertimeMinutes());
        }
        return new AttendanceSummary(late, earlyLeave, absent, remote, field, trip, work, overtime, night, holiday,
                holidayOvertime);
    }

    /** 기록 없는 날 — 근무일이면 휴직 · 결근(지난 날) · 미기록(오늘 이후), 그 밖에는 상태가 없다. */
    private static AttendanceDayStatus statusWithoutRecord(DayInput in) {
        if (!in.workday() || in.employment() == Employment.NOT_TRACKED) {
            return null;
        }
        if (in.employment() == Employment.ON_LEAVE) {
            return AttendanceDayStatus.ON_LEAVE;
        }
        return in.date().isBefore(in.today()) ? AttendanceDayStatus.ABSENT : AttendanceDayStatus.NOT_RECORDED;
    }

    private static AttendanceDay noRecord(DayInput in, AttendanceDayStatus status) {
        return new AttendanceDay(in.date(), in.workday(), in.holidayName(), status, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** 출근~퇴근이 야간 시간대와 겹치는 분. 야간 시간대는 보통 자정을 넘는다(22:00–06:00). */
    private static int nightMinutes(LocalDateTime checkIn, LocalDateTime checkOut, LocalTime nightStart,
                                    LocalTime nightEnd) {
        long total = 0;
        for (LocalDate d = checkIn.toLocalDate().minusDays(1); !d.isAfter(checkOut.toLocalDate()); d = d.plusDays(1)) {
            LocalDateTime start = d.atTime(nightStart);
            LocalDateTime end = nightEnd.isAfter(nightStart) ? d.atTime(nightEnd) : d.plusDays(1).atTime(nightEnd);
            LocalDateTime from = checkIn.isAfter(start) ? checkIn : start;
            LocalDateTime to = checkOut.isBefore(end) ? checkOut : end;
            if (from.isBefore(to)) {
                total += ChronoUnit.MINUTES.between(from, to);
            }
        }
        return (int) total;
    }

    private static Employment employmentOn(LocalDate date, LocalDate trackingStart, List<StatusChange> changes) {
        if (date.isBefore(trackingStart)) {
            return Employment.NOT_TRACKED;
        }
        EmpStatus status = EmpStatus.ACTIVE;
        for (StatusChange change : changes) {
            if (!change.effectiveDate().isAfter(date)) {
                status = change.status();
            }
        }
        return switch (status) {
            case ACTIVE -> Employment.ACTIVE;
            case ON_LEAVE -> Employment.ON_LEAVE;
            case RESIGNED -> Employment.NOT_TRACKED;
        };
    }

    private static LocalDateTime minute(OffsetDateTime time) {
        return time.atZoneSameInstant(ClockConfig.ZONE).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);
    }

    private static OffsetDateTime seoul(OffsetDateTime time) {
        return time.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }

    private static int zeroIfNull(Integer minutes) {
        return minutes == null ? 0 : minutes;
    }

    private record StatusChange(LocalDate effectiveDate, EmpStatus status) {
    }

    private record Row(AttendanceStatus status, WorkType workType, OffsetDateTime checkInAt, OffsetDateTime checkOutAt) {
    }
}
