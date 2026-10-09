package com.nexuslabs.hr.domain.dashboard.service;

import com.nexuslabs.hr.domain.account.dto.MeResponse;
import com.nexuslabs.hr.domain.account.service.MeService;
import com.nexuslabs.hr.domain.assignment.service.AssignmentQueryService;
import com.nexuslabs.hr.domain.attendance.dto.MonthlyAttendance;
import com.nexuslabs.hr.domain.attendance.service.AttendanceCalculator;
import com.nexuslabs.hr.domain.attendance.service.AttendanceService;
import com.nexuslabs.hr.domain.attendance.service.BusinessTripService;
import com.nexuslabs.hr.domain.attendance.service.ExpenseClaimService;
import com.nexuslabs.hr.domain.attendance.service.OvertimeService;
import com.nexuslabs.hr.domain.attendance.service.TodayStatus;
import com.nexuslabs.hr.domain.attendance.service.TodayStatusReader;
import com.nexuslabs.hr.domain.company.dto.SetupStatusResponse;
import com.nexuslabs.hr.domain.company.service.CompanySetupService;
import com.nexuslabs.hr.domain.dashboard.dto.DashboardHome;
import com.nexuslabs.hr.domain.evaluation.dto.MyEvaluation;
import com.nexuslabs.hr.domain.evaluation.service.EvalResultService;
import com.nexuslabs.hr.domain.leave.service.LeaveBalanceService;
import com.nexuslabs.hr.domain.leave.service.LeaveRequestService;
import com.nexuslabs.hr.domain.payroll.service.PayslipService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import com.nexuslabs.hr.global.permission.PermissionScope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 홈 대시보드(F-DASH-01–05, API 설계서 13장). 영역마다 이미 있는 메서드를 모아 부르고(역할 분담 v2 2.1),
 * 회사 · 내 조직 집계만 여기서 SQL 로 센다. 저장하지 않고 원천 데이터에서 바로 집계한다(BR-DASH-001).
 */
@Service
public class DashboardService {

    private static final int RECENT_ASSIGNMENTS = 10;

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;
    private final ScopeResolver scopeResolver;
    private final MeService meService;
    private final AttendanceService attendanceService;
    private final AttendanceCalculator attendanceCalculator;
    private final TodayStatusReader todayStatusReader;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;
    private final OvertimeService overtimeService;
    private final BusinessTripService businessTripService;
    private final ExpenseClaimService expenseClaimService;
    private final PayslipService payslipService;
    private final EvalResultService evalResultService;
    private final AssignmentQueryService assignmentQueryService;
    private final CompanySetupService companySetupService;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, PermissionReader permissionReader, ScopeResolver scopeResolver,
                            MeService meService, AttendanceService attendanceService,
                            AttendanceCalculator attendanceCalculator, TodayStatusReader todayStatusReader,
                            LeaveBalanceService leaveBalanceService, LeaveRequestService leaveRequestService,
                            OvertimeService overtimeService, BusinessTripService businessTripService,
                            ExpenseClaimService expenseClaimService, PayslipService payslipService,
                            EvalResultService evalResultService, AssignmentQueryService assignmentQueryService,
                            CompanySetupService companySetupService, Clock clock) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
        this.scopeResolver = scopeResolver;
        this.meService = meService;
        this.attendanceService = attendanceService;
        this.attendanceCalculator = attendanceCalculator;
        this.todayStatusReader = todayStatusReader;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveRequestService = leaveRequestService;
        this.overtimeService = overtimeService;
        this.businessTripService = businessTripService;
        this.expenseClaimService = expenseClaimService;
        this.payslipService = payslipService;
        this.evalResultService = evalResultService;
        this.assignmentQueryService = assignmentQueryService;
        this.companySetupService = companySetupService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardHome home(LoginUser user) {
        Map<PermissionCode, PermissionScope> granted = permissionReader.permissionsOf(user);
        MeResponse.Todos todos = meService.todos(user);
        YearMonth month = YearMonth.now(clock);

        DashboardHome.Company company = granted.containsKey(PermissionCode.DASHBOARD_COMPANY) ? company(user, month) : null;
        List<Long> leadOrgUnitIds = scopeResolver.leadOrgUnitIds(user);
        DashboardHome.MyOrg myOrg = leadOrgUnitIds.isEmpty() ? null : myOrg(user, leadOrgUnitIds, month);
        DashboardHome.Admin admin = admin(user, granted, todos);

        return new DashboardHome(me(user), new DashboardHome.Todos(todos.approvalsPending(), todos.evaluationsToSubmit()),
                company, myOrg, admin);
    }

    // ------------------------------------------------------------------ 개인

    private DashboardHome.Me me(LoginUser user) {
        long cid = user.companyId();
        long me = user.employeeId();
        boolean payrollEligible = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT payroll_eligible FROM employee WHERE id = ? AND company_id = ?", Boolean.class, me, cid));
        Optional<MyEvaluation> latest = evalResultService.mine(user).stream().findFirst();
        return new DashboardHome.Me(attendanceService.today(user), leaveBalanceService.mine(user, null).balances(),
                payrollEligible ? payslipService.latest(cid, me) : null,
                latest.map(e -> new DashboardHome.LatestEvaluation(e.evaluationId(), e.cycleName(), e.totalScore(),
                        e.confirmedAt())).orElse(null),
                new DashboardHome.MyPending(leaveRequestService.countPending(cid, me),
                        overtimeService.countPending(cid, me), businessTripService.countPending(cid, me),
                        expenseClaimService.countPending(cid, me)));
    }

    // ------------------------------------------------------------------ 회사 (DASHBOARD_COMPANY)

    private DashboardHome.Company company(LoginUser user, YearMonth month) {
        long cid = user.companyId();
        Map<String, Long> byStatus = new HashMap<>();
        jdbc.query("SELECT status::text AS status, count(*) AS n FROM employee WHERE company_id = ? GROUP BY status",
                rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("n"));
                },
                cid);
        List<DashboardHome.TypeCount> byType = jdbc.query("""
                        SELECT t.id, t.name, count(e.id) AS n FROM employment_type t
                        JOIN employee e ON e.employment_type_id = t.id AND e.company_id = t.company_id
                                       AND e.status IN ('ACTIVE', 'ON_LEAVE')
                        WHERE t.company_id = ?
                        GROUP BY t.id, t.name, t.sort_order ORDER BY t.sort_order, t.id
                        """,
                (rs, i) -> new DashboardHome.TypeCount(rs.getLong("id"), rs.getString("name"), rs.getLong("n")), cid);
        DashboardHome.Headcount headcount = new DashboardHome.Headcount(byStatus.getOrDefault("ACTIVE", 0L),
                byStatus.getOrDefault("ON_LEAVE", 0L), byStatus.getOrDefault("RESIGNED", 0L), orgCounts(cid), byType);

        List<DashboardHome.Vacation> vacations = jdbc.query("""
                        SELECT r.id, e.id AS employee_id, e.name AS employee_name, o.name AS org_unit_name,
                               t.name AS leave_type_name, r.start_date, r.end_date, r.days
                        FROM leave_request r
                             JOIN employee e ON e.id = r.employee_id AND e.company_id = r.company_id
                             JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                             JOIN leave_type t ON t.id = r.leave_type_id AND t.company_id = r.company_id
                        WHERE r.company_id = ? AND r.status IN ('APPROVED', 'CANCEL_REQUESTED')
                          AND r.start_date <= ? AND r.end_date >= ?
                        ORDER BY r.start_date, e.employee_no, r.id
                        """,
                (rs, i) -> new DashboardHome.Vacation(rs.getLong("id"), rs.getLong("employee_id"),
                        rs.getString("employee_name"), rs.getString("org_unit_name"), rs.getString("leave_type_name"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                        rs.getInt("days")),
                cid, Date.valueOf(month.atEndOfMonth()), Date.valueOf(month.atDay(1)));
        return new DashboardHome.Company(headcount, vacations, leaveUsage(cid, null, month),
                assignmentQueryService.recent(cid, RECENT_ASSIGNMENTS));
    }

    /** 활성 조직 트리를 한 번 읽어 최상위 바로 아래 조직부터 하위 포함 인원을 센다. */
    private List<DashboardHome.OrgCount> orgCounts(long cid) {
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        List<Long> order = new ArrayList<>();
        Long[] root = {null};
        jdbc.query("SELECT id, parent_id, name FROM org_unit WHERE company_id = ? AND is_active ORDER BY sort_order, id",
                rs -> {
                    long id = rs.getLong("id");
                    Long p = rs.getObject("parent_id", Long.class);
                    names.put(id, rs.getString("name"));
                    order.add(id);
                    if (p == null) {
                        root[0] = id;
                    } else {
                        parent.put(id, p);
                    }
                },
                cid);
        for (long id : order) {
            Long p = parent.get(id);
            if (p != null) {
                children.computeIfAbsent(p, k -> new ArrayList<>()).add(id);
            }
        }
        Map<Long, Long> direct = new HashMap<>();
        jdbc.query("""
                        SELECT org_unit_id, count(*) AS n FROM employee
                        WHERE company_id = ? AND status IN ('ACTIVE', 'ON_LEAVE') GROUP BY org_unit_id
                        """,
                rs -> {
                    direct.put(rs.getLong("org_unit_id"), rs.getLong("n"));
                },
                cid);
        if (root[0] == null) {
            return List.of();
        }
        return children.getOrDefault(root[0], List.of()).stream()
                .map(id -> orgNode(id, names, children, direct)).toList();
    }

    private static DashboardHome.OrgCount orgNode(long id, Map<Long, String> names, Map<Long, List<Long>> children,
                                                  Map<Long, Long> direct) {
        List<DashboardHome.OrgCount> sub = children.getOrDefault(id, List.of()).stream()
                .map(c -> orgNode(c, names, children, direct)).toList();
        long count = direct.getOrDefault(id, 0L) + sub.stream().mapToLong(DashboardHome.OrgCount::count).sum();
        return new DashboardHome.OrgCount(id, names.get(id), count, sub);
    }

    /** 그 달의 휴가 근태(ON_VACATION) 일수를 종류별로. employeeIds 가 null 이면 회사 전체. */
    private List<DashboardHome.LeaveUsage> leaveUsage(long cid, Collection<Long> employeeIds, YearMonth month) {
        List<Object> args = new ArrayList<>(List.of(cid, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth())));
        String employeeFilter = "";
        if (employeeIds != null) {
            employeeFilter = " AND a.employee_id = ANY(?)";
            args.add(employeeIds.toArray(Long[]::new));
        }
        return jdbc.query("""
                        SELECT t.id, t.name, count(*) AS days
                        FROM attendance a
                             JOIN leave_request r ON r.id = a.leave_request_id AND r.company_id = a.company_id
                             JOIN leave_type t ON t.id = r.leave_type_id AND t.company_id = r.company_id
                        WHERE a.company_id = ? AND a.status = 'ON_VACATION' AND a.work_date BETWEEN ? AND ?
                        """ + employeeFilter + """

                        GROUP BY t.id, t.name, t.sort_order ORDER BY t.sort_order, t.id
                        """,
                (rs, i) -> new DashboardHome.LeaveUsage(rs.getLong("id"), rs.getString("name"), rs.getLong("days")),
                args.toArray());
    }

    // ------------------------------------------------------------------ 내 조직 (조직장)

    private DashboardHome.MyOrg myOrg(LoginUser user, List<Long> leadOrgUnitIds, YearMonth month) {
        long cid = user.companyId();
        List<Long> members = jdbc.queryForList("""
                        WITH RECURSIVE sub AS (
                            SELECT id FROM org_unit WHERE company_id = ? AND id = ANY(?)
                            UNION
                            SELECT c.id FROM org_unit c JOIN sub s ON c.parent_id = s.id WHERE c.company_id = ?)
                        SELECT e.id FROM employee e
                        WHERE e.company_id = ? AND e.status IN ('ACTIVE', 'ON_LEAVE') AND e.org_unit_id IN (SELECT id FROM sub)
                        """,
                Long.class, cid, leadOrgUnitIds.toArray(Long[]::new), cid, cid);
        String names = String.join(", ", jdbc.queryForList(
                "SELECT name FROM org_unit WHERE company_id = ? AND id = ANY(?) ORDER BY sort_order, id",
                String.class, cid, leadOrgUnitIds.toArray(Long[]::new)));

        Map<Long, TodayStatus> today = todayStatusReader.today(cid, members);
        long working = 0, notCheckedIn = 0, onVacation = 0, onTrip = 0, onLeave = 0;
        for (TodayStatus s : today.values()) {
            switch (s) {
                case OFFICE, REMOTE, FIELD, OFF_WORK -> working++;
                case BEFORE_WORK -> notCheckedIn++;
                case ON_VACATION -> onVacation++;
                case BUSINESS_TRIP -> onTrip++;
                case ON_LEAVE -> onLeave++;
            }
        }
        long late = attendanceCalculator.months(cid, members, month).values().stream()
                .map(MonthlyAttendance::summary).mapToLong(s -> s.lateCount()).sum();
        return new DashboardHome.MyOrg(names, members.size(),
                new DashboardHome.TeamToday(working, notCheckedIn, onVacation, onTrip, onLeave), late,
                leaveUsage(cid, members, month));
    }

    // ------------------------------------------------------------------ 관리 배지

    private DashboardHome.Admin admin(LoginUser user, Map<PermissionCode, PermissionScope> granted,
                                      MeResponse.Todos todos) {
        Optional<DashboardHome.SetupProgress> setup = null;
        if (granted.containsKey(PermissionCode.COMPANY_MANAGE)) {
            SetupStatusResponse status = companySetupService.status(user);
            setup = status.setupCompleted() ? Optional.empty() : Optional.of(new DashboardHome.SetupProgress(
                    (int) status.steps().stream().filter(SetupStatusResponse.Step::done).count(), status.steps().size()));
        }
        if (setup == null && todos.reassignNeeded() == null && todos.attendanceCorrections() == null) {
            return null;
        }
        return new DashboardHome.Admin(setup, todos.reassignNeeded(), todos.attendanceCorrections());
    }
}
