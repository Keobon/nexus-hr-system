package com.nexuslabs.hr.domain.assignment.service;

import com.nexuslabs.hr.domain.assignment.dto.AssignmentItem;
import com.nexuslabs.hr.domain.assignment.dto.CurrentAssignment;
import com.nexuslabs.hr.domain.assignment.dto.MyAssignments;
import com.nexuslabs.hr.domain.assignment.entity.AssignmentType;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 발령 조회(F-ASSIGN-02 · 03). 여러 테이블을 묶는 조회라 JDBC 로 하고 모든 쿼리에 company_id 조건을 넣는다.
 * 팀 범위(ASSIGNMENT_READ TEAM)면 내가 조직장인 조직과 하위 조직의 직원만 나온다(BR-EMP-003).
 */
@Service
public class AssignmentQueryService {

    private final JdbcTemplate jdbc;
    private final ScopeResolver scopeResolver;

    public AssignmentQueryService(JdbcTemplate jdbc, ScopeResolver scopeResolver) {
        this.jdbc = jdbc;
        this.scopeResolver = scopeResolver;
    }

    /** 발령 이력. 새 발령부터(발효일 → 등록 순의 역순). from · to 는 발효일 기준이고 양 끝을 포함한다. */
    @Transactional(readOnly = true)
    public PageImpl<AssignmentItem> list(LoginUser user, Long employeeId, LocalDate from, LocalDate to,
                                         AssignmentType type, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.invalidFields(Map.of("to", "종료일은 시작일 이후여야 합니다"));
        }
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.ASSIGNMENT_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder where = new StringBuilder(" WHERE h.company_id = ?");
        List<Object> args = new ArrayList<>(List.of(user.companyId()));
        if (!scope.all()) {
            where.append(" AND h.employee_id = ANY(?)");
            args.add(scope.employeeIds().toArray(Long[]::new));
        }
        if (employeeId != null) {
            where.append(" AND h.employee_id = ?");
            args.add(employeeId);
        }
        if (from != null) {
            where.append(" AND h.effective_date >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND h.effective_date <= ?");
            args.add(Date.valueOf(to));
        }
        if (type != null) {
            where.append(" AND h.assignment_type = ?::assignment_type");
            args.add(type.name());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM assignment_history h" + where, Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<AssignmentItem> rows = jdbc.query(SELECT + where + " ORDER BY h.effective_date DESC, h.id DESC LIMIT ? OFFSET ?",
                this::map, pageArgs.toArray());
        return new PageImpl<>(rows, pageable, total);
    }

    /** 직원별 현재 발령. 재직 · 휴직 직원만, orgUnitId 는 하위 조직까지. 사원번호 순. */
    @Transactional(readOnly = true)
    public PageImpl<CurrentAssignment> current(LoginUser user, Long orgUnitId, Pageable pageable) {
        Scope scope = scopeResolver.scopeOf(user, PermissionCode.ASSIGNMENT_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder where = new StringBuilder(" WHERE e.company_id = ? AND e.status IN ('ACTIVE', 'ON_LEAVE')");
        List<Object> args = new ArrayList<>(List.of(user.companyId()));
        if (!scope.all()) {
            where.append(" AND e.id = ANY(?)");
            args.add(scope.employeeIds().toArray(Long[]::new));
        }
        if (orgUnitId != null) {
            where.append("""
                     AND e.org_unit_id IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT c.id FROM org_unit c JOIN sub s ON c.parent_id = s.id WHERE c.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(orgUnitId, user.companyId(), user.companyId()));
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM employee e" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<CurrentAssignment> rows = jdbc.query(CURRENT_SELECT + where + " ORDER BY e.employee_no, e.id LIMIT ? OFFSET ?",
                this::mapCurrent, pageArgs.toArray());
        return new PageImpl<>(rows, pageable, total);
    }

    /** 내 현재 값 + 이력. 토큰의 직원 ID 로만(BR-AUTH-001). */
    @Transactional(readOnly = true)
    public MyAssignments mine(LoginUser user) {
        CurrentAssignment current = jdbc.query(CURRENT_SELECT + " WHERE e.company_id = ? AND e.id = ?",
                        this::mapCurrent, user.companyId(), user.employeeId()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<AssignmentItem> history = jdbc.query(SELECT
                        + " WHERE h.company_id = ? AND h.employee_id = ? ORDER BY h.effective_date DESC, h.id DESC",
                this::map, user.companyId(), user.employeeId());
        return new MyAssignments(current, history);
    }

    /** 최근 발령(대시보드 홈 F-DASH-04) — 회사 전체, 새 발령부터. 권한 범위는 부르는 쪽이 본다(DASHBOARD_COMPANY). */
    @Transactional(readOnly = true)
    public List<AssignmentItem> recent(long companyId, int limit) {
        return jdbc.query(SELECT + " WHERE h.company_id = ? ORDER BY h.effective_date DESC, h.id DESC LIMIT ?",
                this::map, companyId, limit);
    }

    /** 방금 등록 · 정정한 한 줄(응답용). */
    AssignmentItem item(long companyId, long id) {
        return jdbc.queryForObject(SELECT + " WHERE h.company_id = ? AND h.id = ?", this::map, companyId, id);
    }

    private AssignmentItem map(ResultSet rs, int i) throws SQLException {
        Long correctionOfId = rs.getObject("correction_of_id", Long.class);
        AssignmentItem.Original original = correctionOfId == null ? null : new AssignmentItem.Original(correctionOfId,
                AssignmentType.valueOf(rs.getString("o_type")), rs.getObject("o_effective_date", LocalDate.class),
                rs.getString("o_to_org_unit_name"), rs.getString("o_to_job_grade_name"),
                rs.getString("o_to_job_title_name"), rs.getString("o_reason"));
        return new AssignmentItem(rs.getLong("id"), rs.getLong("employee_id"), rs.getString("employee_no"),
                rs.getString("employee_name"), AssignmentType.valueOf(rs.getString("assignment_type")),
                rs.getObject("effective_date", LocalDate.class),
                rs.getObject("from_org_unit_id", Long.class), rs.getString("from_org_unit_name"),
                rs.getObject("from_job_grade_id", Long.class), rs.getString("from_job_grade_name"),
                rs.getObject("from_job_title_id", Long.class), rs.getString("from_job_title_name"),
                rs.getLong("to_org_unit_id"), rs.getString("to_org_unit_name"),
                rs.getLong("to_job_grade_id"), rs.getString("to_job_grade_name"),
                rs.getObject("to_job_title_id", Long.class), rs.getString("to_job_title_name"),
                rs.getString("reason"), correctionOfId, original, rs.getObject("corrected_by_id", Long.class),
                rs.getLong("created_by"), rs.getString("created_by_name"),
                rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ClockConfig.ZONE)
                        .toOffsetDateTime());
    }

    private CurrentAssignment mapCurrent(ResultSet rs, int i) throws SQLException {
        return new CurrentAssignment(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                rs.getLong("org_unit_id"), rs.getString("org_unit_name"), rs.getObject("job_grade_id", Long.class),
                rs.getString("job_grade_name"), rs.getObject("job_title_id", Long.class),
                rs.getString("job_title_name"), rs.getObject("last_assigned_date", LocalDate.class));
    }

    /** 정정본은 원본과 같은 발효일이라 최근 발령일은 발효일의 최댓값이다. */
    private static final String CURRENT_SELECT = """
            SELECT e.id, e.employee_no, e.name, e.org_unit_id, o.name AS org_unit_name, e.job_grade_id,
                   g.name AS job_grade_name, e.job_title_id, t.name AS job_title_name,
                   (SELECT max(h.effective_date) FROM assignment_history h
                    WHERE h.company_id = e.company_id AND h.employee_id = e.id) AS last_assigned_date
            FROM employee e
                 JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                 LEFT JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                 LEFT JOIN job_title t ON t.id = e.job_title_id AND t.company_id = e.company_id
            """;

    private static final String SELECT = """
            SELECT h.id, h.employee_id, e.employee_no, e.name AS employee_name, h.assignment_type::text AS assignment_type,
                   h.effective_date, h.from_org_unit_id, fo.name AS from_org_unit_name, h.from_job_grade_id,
                   fg.name AS from_job_grade_name, h.from_job_title_id, ft.name AS from_job_title_name,
                   h.to_org_unit_id, tou.name AS to_org_unit_name, h.to_job_grade_id, tg.name AS to_job_grade_name,
                   h.to_job_title_id, tt.name AS to_job_title_name, h.reason, h.correction_of_id,
                   oh.assignment_type::text AS o_type, oh.effective_date AS o_effective_date, oh.reason AS o_reason,
                   oo.name AS o_to_org_unit_name, og.name AS o_to_job_grade_name, ot.name AS o_to_job_title_name,
                   (SELECT c.id FROM assignment_history c
                    WHERE c.company_id = h.company_id AND c.correction_of_id = h.id) AS corrected_by_id,
                   h.created_by, cb.name AS created_by_name, h.created_at
            FROM assignment_history h
                 JOIN employee e ON e.id = h.employee_id AND e.company_id = h.company_id
                 LEFT JOIN org_unit fo ON fo.id = h.from_org_unit_id AND fo.company_id = h.company_id
                 LEFT JOIN job_grade fg ON fg.id = h.from_job_grade_id AND fg.company_id = h.company_id
                 LEFT JOIN job_title ft ON ft.id = h.from_job_title_id AND ft.company_id = h.company_id
                 JOIN org_unit tou ON tou.id = h.to_org_unit_id AND tou.company_id = h.company_id
                 JOIN job_grade tg ON tg.id = h.to_job_grade_id AND tg.company_id = h.company_id
                 LEFT JOIN job_title tt ON tt.id = h.to_job_title_id AND tt.company_id = h.company_id
                 LEFT JOIN assignment_history oh ON oh.id = h.correction_of_id AND oh.company_id = h.company_id
                 LEFT JOIN org_unit oo ON oo.id = oh.to_org_unit_id AND oo.company_id = h.company_id
                 LEFT JOIN job_grade og ON og.id = oh.to_job_grade_id AND og.company_id = h.company_id
                 LEFT JOIN job_title ot ON ot.id = oh.to_job_title_id AND ot.company_id = h.company_id
                 LEFT JOIN employee cb ON cb.id = h.created_by AND cb.company_id = h.company_id
            """;
}
