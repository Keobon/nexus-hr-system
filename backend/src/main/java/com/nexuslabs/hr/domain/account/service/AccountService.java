package com.nexuslabs.hr.domain.account.service;

import com.nexuslabs.hr.domain.account.dto.AccountRow;
import com.nexuslabs.hr.domain.account.dto.AccountUpdateRequest;
import com.nexuslabs.hr.domain.account.dto.TemporaryPasswordResponse;
import com.nexuslabs.hr.domain.company.service.CompanyBootstrapService;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 계정(F-AUTH-04 · F-AUTH-06). 계정 ID 대신 직원 ID로 다룬다(직원 1명 = 계정 1개).
 * 회사에는 활성 최고 관리자가 1명 이상 있어야 한다(BR-ROLE-002).
 */
@Service
public class AccountService {

    /** 정렬에 쓸 수 있는 필드 → 컬럼. 그 밖의 sort 값은 무시한다(SQL 주입 방지). */
    private static final Map<String, String> SORTABLE = Map.of(
            "employeeNo", "e.employee_no", "name", "e.name", "lastLoginAt", "a.last_login_at");

    private static final String FROM = """
             FROM account a
             JOIN employee e ON e.id = a.employee_id AND e.company_id = a.company_id
             JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
             JOIN role r ON r.id = a.role_id AND r.company_id = a.company_id
            """;

    /** 첫 번째 인자는 지금 시각(잠금 여부 계산). */
    private static final String SELECT = """
            SELECT e.id, e.employee_no, e.name, e.email, o.name AS org_unit_name, r.id AS role_id,
                   r.name AS role_name, a.is_active, a.locked_until, a.last_login_at,
                   COALESCE(a.locked_until > ?, FALSE) AS locked
            """ + FROM;

    private static final RowMapper<AccountRow> ROW_MAPPER = (rs, i) -> new AccountRow(rs.getLong("id"),
            rs.getString("employee_no"), rs.getString("name"), rs.getString("email"), rs.getString("org_unit_name"),
            rs.getLong("role_id"), rs.getString("role_name"), rs.getBoolean("is_active"), rs.getBoolean("locked"),
            rs.getObject("locked_until", OffsetDateTime.class), rs.getObject("last_login_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                          TemporaryPasswordGenerator passwordGenerator, AuditLogger auditLogger, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /**
     * 직원 등록(B-06)에서 부른다 — 임시 비밀번호로 계정을 만들고 그 비밀번호를 돌려준다(화면에 한 번만 표시).
     * roleId 가 null 이면 기본 역할 "직원". 직원 등록과 같은 트랜잭션 안에서 부른다.
     */
    @Transactional
    public String createForEmployee(long companyId, long employeeId, Long roleId) {
        long role = roleId != null ? requireRole(companyId, roleId)
                : jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?",
                Long.class, companyId, CompanyBootstrapService.EMPLOYEE_ROLE);
        String temporaryPassword = passwordGenerator.generate();
        jdbc.update("""
                        INSERT INTO account (company_id, employee_id, password_hash, role_id, must_change_password)
                        VALUES (?, ?, ?, ?, TRUE)
                        """,
                companyId, employeeId, passwordEncoder.encode(temporaryPassword), role);
        return temporaryPassword;
    }

    @Transactional(readOnly = true)
    public PageImpl<AccountRow> list(LoginUser user, Long roleId, Boolean active, Boolean locked, String keyword,
                                     Pageable pageable) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        StringBuilder where = new StringBuilder(" WHERE a.company_id = ?");
        List<Object> args = new ArrayList<>(List.of(user.companyId()));
        if (roleId != null) {
            where.append(" AND a.role_id = ?");
            args.add(roleId);
        }
        if (active != null) {
            where.append(" AND a.is_active = ?");
            args.add(active);
        }
        if (locked != null) {
            where.append(locked ? " AND a.locked_until > ?" : " AND (a.locked_until IS NULL OR a.locked_until <= ?)");
            args.add(now);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.name ILIKE ? OR e.employee_no ILIKE ? OR e.email ILIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.addAll(List.of(like, like, like));
        }
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(now);                      // SELECT 절의 locked 계산
        pageArgs.addAll(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<AccountRow> rows = jdbc.query(SELECT + where + orderBy(pageable.getSort()) + " LIMIT ? OFFSET ?",
                ROW_MAPPER, pageArgs.toArray());
        return new PageImpl<>(rows, pageable, total);
    }

    /** 역할 변경 · 활성/비활성. 마지막 활성 최고 관리자를 잃게 되면 LAST_SUPER_ADMIN. */
    @Transactional
    public AccountRow update(LoginUser user, long employeeId, AccountUpdateRequest request) {
        AccountState before = lock(user.companyId(), employeeId);
        long newRoleId = request.roleId() != null ? requireRole(user.companyId(), request.roleId()) : before.roleId();
        boolean newActive = request.active() != null ? request.active() : before.active();

        if (newActive && !before.active() && before.resigned()) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED, "퇴직한 직원의 계정은 활성화할 수 없습니다");
        }
        boolean losesSuperAdmin = before.superAdmin() && before.active()
                && (!newActive || !isSystemRole(user.companyId(), newRoleId));
        if (losesSuperAdmin && otherActiveSuperAdmins(user.companyId(), employeeId) == 0) {
            throw new BusinessException(ErrorCode.LAST_SUPER_ADMIN);
        }

        jdbc.update("UPDATE account SET role_id = ?, is_active = ?, updated_at = now() WHERE employee_id = ? AND company_id = ?",
                newRoleId, newActive, employeeId, user.companyId());
        auditLogger.log(user, AuditAction.UPDATE, "ACCOUNT", employeeId,
                Map.of("roleId", before.roleId(), "active", before.active()),
                Map.of("roleId", newRoleId, "active", newActive));
        return row(user, employeeId);
    }

    @Transactional
    public AccountRow unlock(LoginUser user, long employeeId) {
        AccountState before = lock(user.companyId(), employeeId);
        jdbc.update("""
                        UPDATE account SET failed_login_count = 0, locked_until = NULL, updated_at = now()
                        WHERE employee_id = ? AND company_id = ?
                        """,
                employeeId, user.companyId());
        Map<String, Object> beforeValue = new LinkedHashMap<>();
        beforeValue.put("lockedUntil", before.lockedUntil() == null ? null : before.lockedUntil().toString());
        beforeValue.put("failedLoginCount", before.failedLoginCount());
        auditLogger.log(user, AuditAction.EXECUTE, "ACCOUNT_UNLOCK", employeeId, beforeValue, null);
        return row(user, employeeId);
    }

    /** 관리자가 임시 비밀번호를 새로 발급한다(비밀번호 찾기 기능 대신). 감사 로그에 비밀번호는 남기지 않는다. */
    @Transactional
    public TemporaryPasswordResponse resetPassword(LoginUser user, long employeeId) {
        lock(user.companyId(), employeeId);
        String temporaryPassword = passwordGenerator.generate();
        jdbc.update("""
                        UPDATE account SET password_hash = ?, must_change_password = TRUE, updated_at = now()
                        WHERE employee_id = ? AND company_id = ?
                        """,
                passwordEncoder.encode(temporaryPassword), employeeId, user.companyId());
        auditLogger.log(user, AuditAction.EXECUTE, "ACCOUNT_PASSWORD_RESET", employeeId, null, null);
        return new TemporaryPasswordResponse(temporaryPassword);
    }

    private AccountRow row(LoginUser user, long employeeId) {
        return jdbc.query(SELECT + " WHERE a.company_id = ? AND a.employee_id = ?", ROW_MAPPER,
                        OffsetDateTime.now(clock), user.companyId(), employeeId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 대상 계정을 잠그고 읽는다. 다른 회사 직원이면 404. */
    private AccountState lock(long companyId, long employeeId) {
        return jdbc.query("""
                        SELECT a.role_id, a.is_active, a.failed_login_count, a.locked_until, r.is_system,
                               e.status::text = 'RESIGNED' AS resigned
                        FROM account a
                        JOIN role r ON r.id = a.role_id AND r.company_id = a.company_id
                        JOIN employee e ON e.id = a.employee_id AND e.company_id = a.company_id
                        WHERE a.employee_id = ? AND a.company_id = ?
                        FOR UPDATE OF a
                        """,
                (rs, i) -> new AccountState(rs.getLong("role_id"), rs.getBoolean("is_active"),
                        rs.getInt("failed_login_count"), rs.getObject("locked_until", OffsetDateTime.class),
                        rs.getBoolean("is_system"), rs.getBoolean("resigned")),
                employeeId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 나를 뺀 활성 최고 관리자 수. 동시에 두 명을 해제하는 경우를 막으려고 그 계정들을 잠근다. */
    private int otherActiveSuperAdmins(long companyId, long exceptEmployeeId) {
        return jdbc.queryForList("""
                        SELECT a.id FROM account a
                        JOIN role r ON r.id = a.role_id AND r.company_id = a.company_id AND r.is_system
                        JOIN employee e ON e.id = a.employee_id AND e.company_id = a.company_id
                        WHERE a.company_id = ? AND a.is_active AND e.status <> 'RESIGNED' AND a.employee_id <> ?
                        FOR UPDATE OF a
                        """,
                Long.class, companyId, exceptEmployeeId).size();
    }

    private long requireRole(long companyId, long roleId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM role WHERE id = ? AND company_id = ?)",
                Boolean.class, roleId, companyId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "역할을 찾을 수 없습니다");
        }
        return roleId;
    }

    private boolean isSystemRole(long companyId, long roleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_system FROM role WHERE id = ? AND company_id = ?", Boolean.class, roleId, companyId));
    }

    private static String orderBy(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order o : sort) {
            String column = SORTABLE.get(o.getProperty());
            if (column != null) {
                parts.add(column + (o.isAscending() ? " ASC" : " DESC") + " NULLS LAST");
            }
        }
        parts.add("e.employee_no ASC");
        return " ORDER BY " + String.join(", ", parts);
    }

    private record AccountState(long roleId, boolean active, int failedLoginCount, OffsetDateTime lockedUntil,
                                boolean superAdmin, boolean resigned) {
    }
}
