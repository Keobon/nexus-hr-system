package com.nexuslabs.hr.global.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 요청마다 계정 상태를 읽는다. 역할·활성 여부가 바뀌면 다음 요청부터 바로 반영된다.
 * 엔티티(B-02)에 기대지 않으려고 JDBC로 읽는다. company_id 조건을 반드시 넣는다.
 */
@Component
public class AuthAccountReader {

    private final JdbcTemplate jdbc;

    public AuthAccountReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountState> find(long employeeId, long companyId) {
        return jdbc.query("""
                        SELECT a.role_id, a.is_active, a.must_change_password, e.status::text AS status
                        FROM account a JOIN employee e ON e.id = a.employee_id AND e.company_id = a.company_id
                        WHERE a.employee_id = ? AND a.company_id = ?
                        """,
                (rs, i) -> new AccountState(rs.getLong("role_id"), rs.getBoolean("is_active"),
                        rs.getBoolean("must_change_password"), "RESIGNED".equals(rs.getString("status"))),
                employeeId, companyId).stream().findFirst();
    }

    public record AccountState(long roleId, boolean active, boolean mustChangePassword, boolean resigned) {

        public boolean usable() {
            return active && !resigned;
        }
    }
}
