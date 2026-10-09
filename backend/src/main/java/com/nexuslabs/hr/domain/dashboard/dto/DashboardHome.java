package com.nexuslabs.hr.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexuslabs.hr.domain.assignment.dto.AssignmentItem;
import com.nexuslabs.hr.domain.attendance.dto.TodayAttendance;
import com.nexuslabs.hr.domain.leave.dto.LeaveBalance;
import com.nexuslabs.hr.domain.payroll.dto.LatestPayslip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * GET /api/dashboard/home (API 설계서 13장, F-DASH-01–05). 권한에 따라 섹션이 붙거나 빠진다 — 조건이 안 맞는 섹션은
 * **필드 자체를 뺀다**(Java null → 생략). 모든 수치는 원천 테이블에서 바로 집계한다(BR-DASH-001).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardHome(Me me, Todos todos, Company company, MyOrg myOrg, Admin admin) {

    /**
     * 개인 위젯. latestPayslip 은 급여 대상만(아니면 필드 없음, 대상인데 명세서가 없으면 null).
     * latestEvaluation 은 확정된 최근 1건, 없으면 null.
     */
    public record Me(TodayAttendance today, List<LeaveBalance> leaveBalances,
                     @JsonInclude(JsonInclude.Include.NON_NULL) Optional<LatestPayslip> latestPayslip,
                     LatestEvaluation latestEvaluation, MyPending myPending) {
    }

    public record LatestEvaluation(long evaluationId, String cycleName, BigDecimal totalScore,
                                   OffsetDateTime confirmedAt) {
    }

    /** 내가 신청한 승인대기 건수. */
    public record MyPending(long leave, long overtime, long businessTrip, long expenseClaim) {
    }

    public record Todos(long approvalsPending, long evaluationsToSubmit) {
    }

    /** DASHBOARD_COMPANY — 인원(F-DASH-02) · 이번 달 휴가(F-DASH-03) · 최근 발령 10건(F-DASH-04). */
    public record Company(Headcount headcount, List<Vacation> onVacationThisMonth, List<LeaveUsage> leaveUsageThisMonth,
                          List<AssignmentItem> recentAssignments) {
    }

    /** byOrgUnit 은 최상위 바로 아래 조직부터, 펼치면 하위(children). count 는 하위 조직까지 포함한 재직 · 휴직 인원. */
    public record Headcount(long active, long onLeave, long resigned, List<OrgCount> byOrgUnit,
                            List<TypeCount> byEmploymentType) {
    }

    public record OrgCount(long orgUnitId, String orgUnitName, long count, List<OrgCount> children) {
    }

    public record TypeCount(long employmentTypeId, String employmentTypeName, long count) {
    }

    /** 이번 달과 겹치는 승인된 휴가(취소 요청 중 포함), 퇴직자 제외. 시작일 순. */
    public record Vacation(long leaveRequestId, long employeeId, String employeeName, String orgUnitName,
                           String leaveTypeName, LocalDate startDate, LocalDate endDate, int days) {
    }

    /** 이번 달 안에서 쓴 휴가 일수(그 달의 휴가 근태 일수), 종류별. */
    public record LeaveUsage(long leaveTypeId, String leaveTypeName, long days) {
    }

    /**
     * 조직장 — 내가 조직장인 조직과 하위 조직의 재직 · 휴직 직원(F-DASH-05). 여러 조직의 조직장이면 이름을 이어 붙인다.
     * today: working = 사내 · 재택 · 외근 · 퇴근, notCheckedIn = 출근 전, onVacation = 휴가, onTrip = 출장, onLeave = 휴직.
     */
    public record MyOrg(String orgUnitName, long memberCount, TeamToday today, long lateCountThisMonth,
                        List<LeaveUsage> leaveUsageThisMonth) {
    }

    public record TeamToday(long working, long notCheckedIn, long onVacation, long onTrip, long onLeave) {
    }

    /**
     * 관리 배지. setupProgress 는 COMPANY_MANAGE 만(설정을 마쳤으면 null), reassignNeeded 는 APPROVAL_MANAGE,
     * attendanceCorrections 는 ATTENDANCE_MANAGE 만 — 권한이 없으면 필드가 없고, 셋 다 없으면 admin 자체가 없다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Admin(Optional<SetupProgress> setupProgress, Long reassignNeeded, Long attendanceCorrections) {
    }

    /** 마법사 단계 중 데이터가 있는 단계 수 / 전체 단계 수(GET /company/setup 과 같은 기준). */
    public record SetupProgress(int doneSteps, int totalSteps) {
    }
}
