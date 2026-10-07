package com.nexuslabs.hr.domain.leave.service;

import com.nexuslabs.hr.domain.leave.dto.AnnualGrantResult;
import com.nexuslabs.hr.domain.leave.dto.GrantAdjustmentRequest;
import com.nexuslabs.hr.domain.leave.dto.LeaveBalance;
import com.nexuslabs.hr.domain.leave.dto.LeaveGrantItem;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 휴가 부여(F-LEAVE-02, BR-LEAVE-006·007). 부여 내역은 append-only 다 — 고칠 때는 조정 행을 추가한다.
 * 정기·입사 부여는 ux_leave_grant_once 인덱스로 직원·종류·연도마다 1건만 들어간다(ON CONFLICT DO NOTHING).
 */
@Service
public class LeaveGrantService {

    private static final String INSERT_ONCE = """
            INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
            VALUES (?, ?, ?, ?, ?::leave_grant_type, ?)
            ON CONFLICT (employee_id, leave_type_id, leave_year) WHERE grant_type IN ('REGULAR','HIRE') DO NOTHING
            """;

    private static final String SELECT_ITEM = """
            SELECT g.id, g.employee_id, g.leave_type_id, t.name AS leave_type_name, g.leave_year,
                   g.grant_type::text AS grant_type, g.days, g.reason, g.created_by, c.name AS created_by_name,
                   g.created_at
            FROM leave_grant g
            JOIN leave_type t ON t.id = g.leave_type_id AND t.company_id = g.company_id
            LEFT JOIN employee c ON c.id = g.created_by AND c.company_id = g.company_id
            """;

    private static final RowMapper<LeaveGrantItem> ITEM_MAPPER = (rs, i) -> new LeaveGrantItem(rs.getLong("id"),
            rs.getLong("employee_id"), rs.getLong("leave_type_id"), rs.getString("leave_type_name"),
            rs.getInt("leave_year"), LeaveGrantType.valueOf(rs.getString("grant_type")), rs.getInt("days"),
            rs.getString("reason"), rs.getObject("created_by", Long.class), rs.getString("created_by_name"),
            rs.getObject("created_at", OffsetDateTime.class));

    private static final RowMapper<GrantType> TYPE_MAPPER = (rs, i) -> new GrantType(rs.getLong("id"),
            new LeaveTypeRule(rs.getInt("annual_days"), rs.getBoolean("prorate_first_year"),
                    rs.getObject("seniority_start_years", Integer.class),
                    rs.getObject("seniority_interval_years", Integer.class),
                    rs.getObject("seniority_add_days", Integer.class),
                    rs.getObject("seniority_max_days", Integer.class)));

    private final JdbcTemplate jdbc;
    private final LeaveCalculator calculator;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public LeaveGrantService(JdbcTemplate jdbc, LeaveCalculator calculator, AuditLogger auditLogger, Clock clock) {
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /**
     * 연도 일괄 부여. 연도 시작일에 재직중·휴직이던 직원 × 연간 부여일수 > 0 인 활성 종류마다 정기 부여 1건.
     * 그 연도·종류의 정기·입사 부여가 이미 있으면 건너뛴다(두 번 실행해도 같다).
     */
    @Transactional
    public AnnualGrantResult grantAnnual(LoginUser user, int year) {
        LeaveYear leaveYear = calculator.leaveYear(user.companyId(), year);
        List<GrantType> types = grantableTypes(user.companyId());
        // 연도 시작일 당시 재직상태 = 그날까지의 마지막 재직상태 이력(없으면 입사 후 재직중)
        List<Map<String, Object>> employees = jdbc.queryForList("""
                        SELECT e.id, e.hire_date FROM employee e
                        WHERE e.company_id = ? AND e.hire_date <= ?
                          AND COALESCE((SELECT h.status FROM employment_status_history h
                                        WHERE h.employee_id = e.id AND h.company_id = e.company_id
                                          AND h.effective_date <= ?
                                        ORDER BY h.effective_date DESC, h.id DESC LIMIT 1), 'ACTIVE') <> 'RESIGNED'
                        ORDER BY e.id
                        """,
                user.companyId(), Date.valueOf(leaveYear.start()), Date.valueOf(leaveYear.start()));

        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> e : employees) {
            LocalDate hireDate = ((Date) e.get("hire_date")).toLocalDate();
            for (GrantType t : types) {
                rows.add(new Object[]{user.companyId(), e.get("id"), t.id(), year, LeaveGrantType.REGULAR.name(),
                        LeaveCalculator.regularDays(t.rule(), hireDate, leaveYear.start())});
            }
        }
        int created = Arrays.stream(jdbc.batchUpdate(INSERT_ONCE, rows)).map(n -> n > 0 ? 1 : 0).sum();
        AnnualGrantResult result = new AnnualGrantResult(year, created, rows.size() - created);
        auditLogger.log(user, AuditAction.EXECUTE, "LEAVE_GRANT", null, null, result);
        return result;
    }

    /**
     * 직원 등록(B-06)에서 같은 트랜잭션으로 부른다 — 현재 휴가 연도분 입사 부여(입사일이 미래면 입사일이 속한 연도).
     * 이미 정기·입사 부여가 있는 종류는 건너뛴다. 만들어진 부여 내역을 돌려준다(등록 응답의 leaveGrants).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<LeaveGrantItem> grantOnHire(long companyId, long employeeId) {
        LocalDate hireDate = jdbc.query("SELECT hire_date FROM employee WHERE id = ? AND company_id = ?",
                        (rs, i) -> rs.getObject("hire_date", LocalDate.class), employeeId, companyId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        LeaveYear leaveYear = calculator.leaveYearContaining(companyId, hireDate.isAfter(today) ? hireDate : today);

        List<Long> createdIds = new ArrayList<>();
        for (GrantType t : grantableTypes(companyId)) {
            createdIds.addAll(jdbc.queryForList(INSERT_ONCE.strip() + " RETURNING id", Long.class,
                    companyId, employeeId, t.id(), leaveYear.year(), LeaveGrantType.HIRE.name(),
                    LeaveCalculator.hireDays(t.rule(), hireDate, leaveYear)));
        }
        if (createdIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SELECT_ITEM + " WHERE g.company_id = ? AND g.id = ANY(?) ORDER BY t.sort_order, t.id",
                ITEM_MAPPER, companyId, createdIds.toArray(Long[]::new));
    }

    /** 개별 조정(+/−). 잔여가 음수가 되는 조정은 거부한다. */
    @Transactional
    public LeaveGrantItem adjust(LoginUser user, GrantAdjustmentRequest request) {
        if (request.days() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "조정 일수는 0일 수 없습니다");
        }
        lockEmployee(user.companyId(), request.employeeId());
        Boolean active = jdbc.query("SELECT is_active FROM leave_type WHERE id = ? AND company_id = ?",
                        (rs, i) -> rs.getBoolean("is_active"), request.leaveTypeId(), user.companyId())
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!active) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        if (request.days() < 0) {
            LeaveBalance balance = calculator.balance(user.companyId(), request.employeeId(), request.leaveTypeId(),
                    request.leaveYear());
            if (balance.remaining() + request.days() < 0) {
                throw new BusinessException(ErrorCode.LEAVE_INSUFFICIENT_BALANCE,
                        Map.of("remaining", balance.remaining(), "requested", request.days()));
            }
        }
        long id = jdbc.queryForObject("""
                        INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days,
                                                 reason, created_by)
                        VALUES (?, ?, ?, ?, 'ADJUSTMENT', ?, ?, ?) RETURNING id
                        """,
                Long.class, user.companyId(), request.employeeId(), request.leaveTypeId(), request.leaveYear(),
                request.days(), request.reason().trim(), user.employeeId());
        LeaveGrantItem created = item(user.companyId(), id);
        auditLogger.log(user, AuditAction.CREATE, "LEAVE_GRANT", id, null, created);
        return created;
    }

    /** 부여 내역(정기·입사·조정). leaveYear 가 null 이면 모든 연도, 최근 연도부터. */
    @Transactional(readOnly = true)
    public List<LeaveGrantItem> list(LoginUser user, long employeeId, Integer leaveYear) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM employee WHERE id = ? AND company_id = ?)",
                Boolean.class, employeeId, user.companyId());
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return jdbc.query(SELECT_ITEM + """
                         WHERE g.company_id = ? AND g.employee_id = ? AND (?::int IS NULL OR g.leave_year = ?::int)
                        ORDER BY g.leave_year DESC, t.sort_order, t.id, g.id
                        """,
                ITEM_MAPPER, user.companyId(), employeeId, leaveYear, leaveYear);
    }

    private LeaveGrantItem item(long companyId, long grantId) {
        return jdbc.queryForObject(SELECT_ITEM + " WHERE g.id = ? AND g.company_id = ?", ITEM_MAPPER,
                grantId, companyId);
    }

    /** 부여 대상 종류 = 활성 + 연간 부여일수 > 0. */
    private List<GrantType> grantableTypes(long companyId) {
        return jdbc.query("""
                        SELECT id, annual_days, prorate_first_year, seniority_start_years, seniority_interval_years,
                               seniority_add_days, seniority_max_days
                        FROM leave_type WHERE company_id = ? AND is_active AND annual_days > 0
                        ORDER BY sort_order, id
                        """,
                TYPE_MAPPER, companyId);
    }

    /** 같은 직원의 잔여를 바꾸는 작업(조정·신청)이 동시에 돌아 한도를 넘지 않도록 직원 행을 잠근다. */
    private void lockEmployee(long companyId, long employeeId) {
        if (jdbc.queryForList("SELECT id FROM employee WHERE id = ? AND company_id = ? FOR UPDATE",
                Long.class, employeeId, companyId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private record GrantType(long id, LeaveTypeRule rule) {
    }
}
