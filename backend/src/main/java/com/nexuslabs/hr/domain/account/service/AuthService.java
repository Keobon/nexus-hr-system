package com.nexuslabs.hr.domain.account.service;

import com.nexuslabs.hr.domain.account.dto.LoginRequest;
import com.nexuslabs.hr.domain.account.dto.LoginResponse;
import com.nexuslabs.hr.domain.account.dto.PasswordChangeRequest;
import com.nexuslabs.hr.domain.company.service.CompanyRegistrationService;
import com.nexuslabs.hr.global.auth.JwtProvider;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

/** 로그인(F-AUTH-01) · 비밀번호 변경(F-AUTH-03). 로그아웃은 서버 상태가 없다(F-AUTH-02). */
@Service
public class AuthService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final PermissionReader permissionReader;
    private final Clock clock;
    /** 없는 이메일도 비밀번호 검사 시간을 똑같이 쓰게 해서, 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                       PermissionReader permissionReader, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.permissionReader = permissionReader;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-0");
    }

    /** 실패 횟수·잠금은 실패 응답과 함께 저장돼야 하므로 BusinessException 으로 롤백하지 않는다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        LoginAccount account = jdbc.query("""
                        SELECT a.id, a.employee_id, a.company_id, a.role_id, a.password_hash, a.is_active,
                               a.must_change_password, a.failed_login_count, a.locked_until,
                               e.status::text AS status, c.setup_completed
                        FROM employee e
                        JOIN account a ON a.employee_id = e.id AND a.company_id = e.company_id
                        JOIN company c ON c.id = e.company_id
                        WHERE e.email = ?
                        FOR UPDATE OF a
                        """,
                (rs, i) -> new LoginAccount(rs.getLong("id"), rs.getLong("employee_id"), rs.getLong("company_id"),
                        rs.getLong("role_id"), rs.getString("password_hash"), rs.getBoolean("is_active"),
                        rs.getBoolean("must_change_password"), rs.getInt("failed_login_count"),
                        rs.getObject("locked_until", OffsetDateTime.class), "RESIGNED".equals(rs.getString("status")),
                        rs.getBoolean("setup_completed")),
                CompanyRegistrationService.normalizeEmail(request.email())).stream().findFirst().orElse(null);

        if (account == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(now)) {
            throw locked(account.lockedUntil());
        }
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            recordFailure(account, now);
        }
        if (!account.active() || account.resigned()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_INACTIVE);
        }

        jdbc.update("""
                        UPDATE account SET failed_login_count = 0, locked_until = NULL, last_login_at = ?, updated_at = now()
                        WHERE id = ? AND company_id = ?
                        """,
                now, account.id(), account.companyId());
        return new LoginResponse(jwtProvider.issue(account.employeeId(), account.companyId()), next(account));
    }

    @Transactional
    public void changePassword(LoginUser user, PasswordChangeRequest request) {
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM account WHERE employee_id = ? AND company_id = ?",
                String.class, user.employeeId(), user.companyId());
        if (!passwordEncoder.matches(request.currentPassword(), hash)) {
            throw new BusinessException(ErrorCode.AUTH_WRONG_PASSWORD);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "새 비밀번호가 현재 비밀번호와 같습니다");
        }
        jdbc.update("""
                        UPDATE account SET password_hash = ?, must_change_password = FALSE, updated_at = now()
                        WHERE employee_id = ? AND company_id = ?
                        """,
                passwordEncoder.encode(request.newPassword()), user.employeeId(), user.companyId());
    }

    /** 5회째 실패면 30분 잠그고 횟수를 0으로 되돌린다(잠금이 풀린 뒤 다시 5회). */
    private void recordFailure(LoginAccount account, OffsetDateTime now) {
        int failures = account.failedLoginCount() + 1;
        if (failures >= MAX_FAILURES) {
            OffsetDateTime lockedUntil = now.plus(LOCK_DURATION);
            jdbc.update("""
                            UPDATE account SET failed_login_count = 0, locked_until = ?, updated_at = now()
                            WHERE id = ? AND company_id = ?
                            """,
                    lockedUntil, account.id(), account.companyId());
            throw locked(lockedUntil);
        }
        jdbc.update("UPDATE account SET failed_login_count = ?, updated_at = now() WHERE id = ? AND company_id = ?",
                failures, account.id(), account.companyId());
        throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    private LoginNext next(LoginAccount account) {
        if (account.mustChangePassword()) {
            return LoginNext.CHANGE_PASSWORD;
        }
        if (!account.setupCompleted()) {
            LoginUser user = new LoginUser(account.employeeId(), account.companyId(), account.roleId(), false);
            if (permissionReader.permissionsOf(user).containsKey(PermissionCode.COMPANY_MANAGE)) {
                return LoginNext.SETUP_WIZARD;
            }
        }
        return LoginNext.HOME;
    }

    private static BusinessException locked(OffsetDateTime lockedUntil) {
        return new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED, "비밀번호를 여러 번 틀려 계정이 잠겼습니다. 잠시 후 다시 시도하세요",
                Map.of("lockedUntil", lockedUntil.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime().toString()));
    }

    private record LoginAccount(long id, long employeeId, long companyId, long roleId, String passwordHash,
                                boolean active, boolean mustChangePassword, int failedLoginCount,
                                OffsetDateTime lockedUntil, boolean resigned, boolean setupCompleted) {
    }
}
