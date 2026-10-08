package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 근태(F-ATT-01–05). 지금은 휴가 연동(F-ATT-05)만 있다 — 출퇴근 · 조회 · 정정은 B-11 의 다음 PR 에서 더한다.
 * 휴가 영역(JDBC)이 승인 트랜잭션 안에서 부르므로 JDBC 로 만들고, 모든 쿼리에 company_id 조건을 직접 넣는다.
 */
@Service
public class AttendanceService {

    private final JdbcTemplate jdbc;
    private final WorkCalendar workCalendar;

    public AttendanceService(JdbcTemplate jdbc, WorkCalendar workCalendar) {
        this.jdbc = jdbc;
        this.workCalendar = workCalendar;
    }

    /**
     * 휴가가 최종 승인될 때 같은 트랜잭션에서 부른다(역할 분담 v2 2.1, BR-APPR-005).
     * 휴가 기간의 근무일마다 휴가 근태(ON_VACATION)를 만든다. 근무일은 승인하는 지금의 근무시간·휴일로 판정한다.
     * 이미 근태가 있는 날(출근 기록)은 덮어쓰지 않고 건너뛴다 — 그날은 정정 대상 목록에 충돌로 나온다(BR-ATT-001).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createLeaveDays(long companyId, long leaveRequestId) {
        LeavePeriod leave = jdbc.query("""
                        SELECT employee_id, start_date, end_date FROM leave_request WHERE id = ? AND company_id = ?
                        """,
                (rs, i) -> new LeavePeriod(rs.getLong("employee_id"), rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class)),
                leaveRequestId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        WorkCalendar.Snapshot calendar = workCalendar.snapshot(companyId);
        List<Object[]> rows = new ArrayList<>();
        for (LocalDate date = leave.startDate(); !date.isAfter(leave.endDate()); date = date.plusDays(1)) {
            if (calendar.isWorkday(date)) {
                rows.add(new Object[]{companyId, leave.employeeId(), Date.valueOf(date), leaveRequestId});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, check_in_method, leave_request_id)
                VALUES (?, ?, ?, 'ON_VACATION', 'SYSTEM', ?)
                ON CONFLICT (employee_id, work_date) DO NOTHING
                """, rows);
    }

    /**
     * 휴가 취소가 승인될 때 같은 트랜잭션에서 부른다. 그 휴가로 만든 휴가 근태만 지운다 —
     * 건너뛴 날의 출근 기록이나, 정정으로 휴가 연결을 끊은 근태는 그대로 남는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteLeaveDays(long companyId, long leaveRequestId) {
        jdbc.update("DELETE FROM attendance WHERE company_id = ? AND leave_request_id = ? AND status = 'ON_VACATION'",
                companyId, leaveRequestId);
    }

    private record LeavePeriod(long employeeId, LocalDate startDate, LocalDate endDate) {
    }
}
