package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.domain.company.service.WorkScheduleView;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 직원들의 오늘 상태를 한 번의 쿼리로 계산한다(직원마다 조회하지 않는다). 직원 목록 · 직원 상세 · 홈이 같은 계산을 쓴다.
 * 휴가·출장이 승인되면 그날의 근태 행이 만들어지므로(BR-ATT-001·005) 오늘 근태 행과 재직상태만 보면 된다.
 * 자정을 넘겨 어제 근무가 이어지는 중이면(F-ATT-02) 오늘 행이 없을 때 어제의 출근 상태 행을 오늘 것으로 본다.
 */
@Component
public class TodayStatusReader {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final WorkCalendar workCalendar;

    public TodayStatusReader(JdbcTemplate jdbc, Clock clock, WorkCalendar workCalendar) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.workCalendar = workCalendar;
    }

    /**
     * 자정을 넘긴 지금, 어제 출근해 아직 퇴근하지 않은 근무를 이어지는 중으로 볼 수 있는지(F-ATT-02, BR-ATT-002).
     * 어제 유효한 근무시간의 야간 시간대 끝(기본 06:00) 전까지만이다 — 그 뒤에는 퇴근을 잊은 것으로 본다.
     * 야간 시간대가 자정을 넘지 않는 회사는 해당 없다.
     */
    public boolean yesterdayStillOpen(long companyId) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        WorkScheduleView schedule = workCalendar.scheduleOn(companyId, now.toLocalDate().minusDays(1));
        return schedule.nightEnd().isBefore(schedule.nightStart()) && now.toLocalTime().isBefore(schedule.nightEnd());
    }

    /** 직원 ID → 오늘 상태. 퇴직자는 오늘 상태가 없어 결과에 들어가지 않는다. */
    public Map<Long, TodayStatus> today(long companyId, Collection<Long> employeeIds) {
        Map<Long, TodayStatus> result = new HashMap<>();
        if (employeeIds.isEmpty()) {
            return result;
        }
        LocalDate today = LocalDate.now(clock);
        jdbc.query("""
                        SELECT e.id, e.status::text AS emp_status,
                               (CASE WHEN a.id IS NOT NULL THEN a.status ELSE y.status END)::text AS attendance_status,
                               (CASE WHEN a.id IS NOT NULL THEN a.work_type ELSE y.work_type END)::text AS work_type
                        FROM employee e
                             LEFT JOIN attendance a ON a.employee_id = e.id AND a.company_id = e.company_id
                                                   AND a.work_date = ?
                             LEFT JOIN attendance y ON y.employee_id = e.id AND y.company_id = e.company_id
                                                   AND y.work_date = ? AND y.status = 'CHECKED_IN' AND ?
                        WHERE e.company_id = ? AND e.id = ANY(?)
                        """,
                rs -> {
                    String attendance = rs.getString("attendance_status");
                    String workType = rs.getString("work_type");
                    TodayStatus status = resolve(EmpStatus.valueOf(rs.getString("emp_status")),
                            attendance == null ? null : AttendanceStatus.valueOf(attendance),
                            workType == null ? null : WorkType.valueOf(workType));
                    if (status != null) {
                        result.put(rs.getLong("id"), status);
                    }
                },
                Date.valueOf(today), Date.valueOf(today.minusDays(1)), yesterdayStillOpen(companyId), companyId,
                employeeIds.toArray(Long[]::new));
        return result;
    }

    /**
     * 재직상태가 먼저다 — 퇴직자는 없음(null), 휴직자는 근태와 관계없이 휴직(BR-ATT-003).
     * 그다음은 오늘 근태 행: 없으면 출근 전, 휴가 · 출장 · 퇴근은 그대로, 출근 중이면 근무 형태.
     */
    static TodayStatus resolve(EmpStatus empStatus, AttendanceStatus attendance, WorkType workType) {
        if (empStatus == EmpStatus.RESIGNED) {
            return null;
        }
        if (empStatus == EmpStatus.ON_LEAVE) {
            return TodayStatus.ON_LEAVE;
        }
        if (attendance == null) {
            return TodayStatus.BEFORE_WORK;
        }
        return switch (attendance) {
            case ON_VACATION -> TodayStatus.ON_VACATION;
            case ON_BUSINESS_TRIP -> TodayStatus.BUSINESS_TRIP;
            case CHECKED_OUT -> TodayStatus.OFF_WORK;
            case CHECKED_IN, MISSING_CHECKOUT -> workType == null ? TodayStatus.OFFICE
                    : TodayStatus.valueOf(workType.name());
        };
    }
}
