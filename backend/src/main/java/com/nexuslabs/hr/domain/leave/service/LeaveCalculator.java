package com.nexuslabs.hr.domain.leave.service;

import com.nexuslabs.hr.domain.leave.dto.LeaveBalance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 휴가 연도 · 부여일수 · 잔여 계산(공통 컴포넌트 ④, API 설계서 15장). 잔여는 저장하지 않고 매번 계산한다(BR-LEAVE-003).
 * 휴가 일수(근무일 수) 계산은 WorkCalendar(B-09)가 들어오면 여기에 추가한다.
 */
@Component
public class LeaveCalculator {

    /** 사용으로 치는 상태. 취소요청 중인 휴가는 취소가 승인되기 전까지 사용이다. */
    static final String USED_STATUSES = "'APPROVED','CANCEL_REQUESTED'";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LeaveCalculator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public LeaveYear leaveYear(long companyId, int year) {
        return LeaveYear.of(year, fiscalYearStartMonth(companyId));
    }

    public LeaveYear leaveYearContaining(long companyId, LocalDate date) {
        return LeaveYear.containing(date, fiscalYearStartMonth(companyId));
    }

    public LeaveYear currentLeaveYear(long companyId) {
        return leaveYearContaining(companyId, LocalDate.now(clock));
    }

    /**
     * 정기 부여일수(F-LEAVE-01 근속 가산). 근속연수는 연도 시작일 기준 만 근속연수.
     * 가산 = (⌊(근속연수 − 시작 근속연수) ÷ 간격⌋ + 1) × 가산 일수, 부여일수 = min(연간 부여일수 + 가산, 최대 일수).
     */
    public static int regularDays(LeaveTypeRule type, LocalDate hireDate, LocalDate yearStart) {
        if (!type.hasSeniority() || hireDate.isAfter(yearStart)) {
            return type.annualDays();
        }
        int years = Period.between(hireDate, yearStart).getYears();
        if (years < type.seniorityStartYears()) {
            return type.annualDays();
        }
        int bonus = ((years - type.seniorityStartYears()) / type.seniorityIntervalYears() + 1) * type.seniorityAddDays();
        return Math.min(type.annualDays() + bonus, type.seniorityMaxDays());
    }

    /**
     * 입사 부여일수(F-LEAVE-02). 입사일이 그 휴가 연도 안이고 첫해 비례 종류면
     * 연간 부여일수 × (입사월부터 연도 말까지 남은 개월 수 ÷ 12)를 내림. 연도 시작일 이전 입사면 정기 부여와 같다.
     */
    public static int hireDays(LeaveTypeRule type, LocalDate hireDate, LeaveYear year) {
        if (hireDate.isBefore(year.start())) {
            return regularDays(type, hireDate, year.start());
        }
        if (!type.prorateFirstYear()) {
            return type.annualDays();
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(hireDate), YearMonth.from(year.end())) + 1;
        return (int) (type.annualDays() * months / 12);
    }

    /** 한 직원·종류·연도의 잔여(차감 여부와 상관없이). 조정·신청 전 확인에 쓴다. */
    public LeaveBalance balance(long companyId, long employeeId, long leaveTypeId, int leaveYear) {
        return jdbc.queryForObject("""
                        SELECT t.id, t.name,
                               (SELECT COALESCE(sum(g.days), 0) FROM leave_grant g
                                WHERE g.company_id = t.company_id AND g.employee_id = ? AND g.leave_type_id = t.id
                                  AND g.leave_year = ?) AS granted,
                               (SELECT COALESCE(sum(r.days), 0) FROM leave_request r
                                WHERE r.company_id = t.company_id AND r.employee_id = ? AND r.leave_type_id = t.id
                                  AND r.leave_year = ? AND r.status IN (%s)) AS used,
                               (SELECT COALESCE(sum(r.days), 0) FROM leave_request r
                                WHERE r.company_id = t.company_id AND r.employee_id = ? AND r.leave_type_id = t.id
                                  AND r.leave_year = ? AND r.status = 'PENDING') AS pending
                        FROM leave_type t WHERE t.id = ? AND t.company_id = ?
                        """.formatted(USED_STATUSES),
                (rs, i) -> LeaveBalance.of(rs.getLong("id"), rs.getString("name"),
                        rs.getInt("granted"), rs.getInt("used"), rs.getInt("pending")),
                employeeId, leaveYear, employeeId, leaveYear, employeeId, leaveYear, leaveTypeId, companyId);
    }

    /**
     * 직원별 차감 종류 잔여(F-LEAVE-05). 비활성 종류는 부여·사용 내역이 있을 때만 보인다.
     * 결과는 employeeIds 순서를 따르지 않으므로 Map 으로 찾는다.
     */
    public Map<Long, List<LeaveBalance>> balances(long companyId, int leaveYear, Collection<Long> employeeIds) {
        Map<Long, List<LeaveBalance>> result = new LinkedHashMap<>();
        if (employeeIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT e.id AS employee_id, t.id AS type_id, t.name, t.is_active,
                       COALESCE(g.days, 0) AS granted, COALESCE(u.used, 0) AS used, COALESCE(u.pending, 0) AS pending
                FROM employee e
                JOIN leave_type t ON t.company_id = e.company_id AND t.deducts_balance
                LEFT JOIN (SELECT employee_id, leave_type_id, sum(days) AS days FROM leave_grant
                           WHERE company_id = ? AND leave_year = ? GROUP BY employee_id, leave_type_id) g
                       ON g.employee_id = e.id AND g.leave_type_id = t.id
                LEFT JOIN (SELECT employee_id, leave_type_id,
                                  sum(days) FILTER (WHERE status IN (%s)) AS used,
                                  sum(days) FILTER (WHERE status = 'PENDING') AS pending
                           FROM leave_request WHERE company_id = ? AND leave_year = ?
                           GROUP BY employee_id, leave_type_id) u
                       ON u.employee_id = e.id AND u.leave_type_id = t.id
                WHERE e.company_id = ? AND e.id = ANY(?)
                ORDER BY e.id, t.sort_order, t.id
                """.formatted(USED_STATUSES), rs -> {
            int granted = rs.getInt("granted");
            int used = rs.getInt("used");
            int pending = rs.getInt("pending");
            List<LeaveBalance> list = result.computeIfAbsent(rs.getLong("employee_id"), k -> new ArrayList<>());
            if (rs.getBoolean("is_active") || granted != 0 || used != 0 || pending != 0) {
                list.add(LeaveBalance.of(rs.getLong("type_id"), rs.getString("name"), granted, used, pending));
            }
        }, companyId, leaveYear, companyId, leaveYear, companyId, employeeIds.toArray(Long[]::new));
        return result;
    }

    private int fiscalYearStartMonth(long companyId) {
        return jdbc.queryForObject("SELECT fiscal_year_start_month FROM company WHERE id = ?", Integer.class, companyId);
    }
}
