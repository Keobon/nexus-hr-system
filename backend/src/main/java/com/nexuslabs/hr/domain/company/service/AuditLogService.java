package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.company.dto.AuditLogItem;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 감사 로그 조회(F-COMP-06). 기록은 AuditLogger, 여기는 읽기만 — 수정 · 삭제는 없다(BR-AUDIT-001). */
@Service
public class AuditLogService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 최신순. from · to 는 서울 날짜 기준이고 양 끝을 포함한다. targetType 은 정확히 같은 값만. */
    @Transactional(readOnly = true)
    public PageImpl<AuditLogItem> list(LoginUser user, LocalDate from, LocalDate to, Long actorId, String targetType,
                                       AuditAction action, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.invalidFields(Map.of("to", "종료일은 시작일 이후여야 합니다"));
        }
        StringBuilder where = new StringBuilder(" WHERE l.company_id = ?");
        List<Object> args = new ArrayList<>(List.of(user.companyId()));
        if (from != null) {
            where.append(" AND l.created_at >= ?");
            args.add(from.atStartOfDay(ClockConfig.ZONE).toOffsetDateTime());
        }
        if (to != null) {
            where.append(" AND l.created_at < ?");
            args.add(to.plusDays(1).atStartOfDay(ClockConfig.ZONE).toOffsetDateTime());
        }
        if (actorId != null) {
            where.append(" AND l.actor_id = ?");
            args.add(actorId);
        }
        if (targetType != null && !targetType.isBlank()) {
            where.append(" AND l.target_type = ?");
            args.add(targetType.trim());
        }
        if (action != null) {
            where.append(" AND l.action = ?::audit_action");
            args.add(action.name());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM audit_log l" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<AuditLogItem> rows = jdbc.query("""
                        SELECT l.id, l.created_at, l.actor_id, e.name AS actor_name, l.action::text AS action,
                               l.target_type, l.target_id, l.before_value::text AS before_value,
                               l.after_value::text AS after_value
                        FROM audit_log l
                        LEFT JOIN employee e ON e.id = l.actor_id AND e.company_id = l.company_id
                        """ + where + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new AuditLogItem(rs.getLong("id"),
                        rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ClockConfig.ZONE)
                                .toOffsetDateTime(),
                        rs.getObject("actor_id", Long.class), rs.getString("actor_name"),
                        AuditAction.valueOf(rs.getString("action")), rs.getString("target_type"),
                        rs.getObject("target_id", Long.class),
                        json(rs.getString("before_value")), json(rs.getString("after_value"))),
                pageArgs.toArray());
        return new PageImpl<>(rows, pageable, total);
    }

    private JsonNode json(String value) {
        return value == null ? null : objectMapper.readTree(value);
    }
}
