package com.nexuslabs.hr.global.audit;

import com.nexuslabs.hr.global.auth.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 감사 로그 기록(BR-AUDIT-001). 기록은 서비스의 트랜잭션 안에서 부른다 — 업무가 롤백되면 로그도 남지 않는다.
 * before/after 는 아무 객체나 넘기면 JSON으로 저장한다(비밀번호·계좌 원문 같은 민감 값은 넘기지 않는다).
 */
@Component
public class AuditLogger {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogger(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 로그인한 사용자의 처리. */
    public void log(LoginUser actor, AuditAction action, String targetType, Long targetId, Object before, Object after) {
        write(actor.companyId(), actor.employeeId(), action, targetType, targetId, before, after);
    }

    /** 로그인 전 처리(회사 등록 등)나 시스템 처리. actorId 가 없으면 null. */
    public void log(long companyId, Long actorId, AuditAction action, String targetType, Long targetId,
                    Object before, Object after) {
        write(companyId, actorId, action, targetType, targetId, before, after);
    }

    private void write(long companyId, Long actorId, AuditAction action, String targetType, Long targetId,
                       Object before, Object after) {
        jdbc.update("""
                        INSERT INTO audit_log (company_id, actor_id, action, target_type, target_id, before_value, after_value)
                        VALUES (?, ?, ?::audit_action, ?, ?, ?::jsonb, ?::jsonb)
                        """,
                companyId, actorId, action.name(), targetType, targetId, toJson(before), toJson(after));
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
