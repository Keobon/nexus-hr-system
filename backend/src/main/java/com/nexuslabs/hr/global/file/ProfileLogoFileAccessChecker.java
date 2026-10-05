package com.nexuslabs.hr.global.file;

import com.nexuslabs.hr.global.auth.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 직원 프로필 사진 · 회사 로고 — 같은 회사 로그인 사용자면 누구나 볼 수 있다. */
@Component
class ProfileLogoFileAccessChecker implements FileAccessChecker {

    private final JdbcTemplate jdbc;

    ProfileLogoFileAccessChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Boolean> canRead(LoginUser user, long fileId) {
        Boolean referenced = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND profile_file_id = ?)
                            OR EXISTS (SELECT 1 FROM company WHERE id = ? AND logo_file_id = ?)
                        """,
                Boolean.class, user.companyId(), fileId, user.companyId(), fileId);
        return Boolean.TRUE.equals(referenced) ? Optional.of(true) : Optional.empty();
    }
}
