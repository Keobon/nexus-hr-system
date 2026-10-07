package com.nexuslabs.hr.domain.leave.service;

import com.nexuslabs.hr.domain.leave.dto.LeaveTypeRequest;
import com.nexuslabs.hr.domain.leave.dto.LeaveTypeResponse;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.response.DeleteResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 휴가 종류 관리(F-LEAVE-01). 부여일수를 바꿔도 이미 부여된 내역은 그대로다(다음 부여부터 적용).
 * 쓰인 적 있는 종류는 지우지 않고 비활성화한다.
 */
@Service
public class LeaveTypeService {

    private static final String SELECT = """
            SELECT id, name, annual_days, deducts_balance, is_paid, prorate_first_year, seniority_start_years,
                   seniority_interval_years, seniority_add_days, seniority_max_days, sort_order, is_active
            FROM leave_type
            """;

    private static final RowMapper<LeaveTypeResponse> ROW_MAPPER = (rs, i) -> new LeaveTypeResponse(
            rs.getLong("id"), rs.getString("name"), rs.getInt("annual_days"), rs.getBoolean("deducts_balance"),
            rs.getBoolean("is_paid"), rs.getBoolean("prorate_first_year"),
            rs.getObject("seniority_start_years", Integer.class), rs.getObject("seniority_interval_years", Integer.class),
            rs.getObject("seniority_add_days", Integer.class), rs.getObject("seniority_max_days", Integer.class),
            rs.getInt("sort_order"), rs.getBoolean("is_active"));

    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;

    public LeaveTypeService(JdbcTemplate jdbc, AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> list(LoginUser user, boolean activeOnly) {
        return jdbc.query(SELECT + " WHERE company_id = ? AND (is_active OR NOT ?) ORDER BY sort_order, id",
                ROW_MAPPER, user.companyId(), activeOnly);
    }

    @Transactional(readOnly = true)
    public LeaveTypeResponse get(long companyId, long leaveTypeId) {
        return jdbc.query(SELECT + " WHERE id = ? AND company_id = ?", ROW_MAPPER, leaveTypeId, companyId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public LeaveTypeResponse create(LoginUser user, LeaveTypeRequest request) {
        validate(request);
        String name = request.name().trim();
        checkNameUnique(user.companyId(), name, null);
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : jdbc.queryForObject(
                "SELECT COALESCE(max(sort_order), 0) + 1 FROM leave_type WHERE company_id = ?",
                Integer.class, user.companyId());
        long id = jdbc.queryForObject("""
                        INSERT INTO leave_type (company_id, name, annual_days, deducts_balance, is_paid, prorate_first_year,
                                                seniority_start_years, seniority_interval_years, seniority_add_days,
                                                seniority_max_days, sort_order, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                        """,
                Long.class, user.companyId(), name, request.annualDays(), request.deductsBalance(),
                !Boolean.FALSE.equals(request.paid()), Boolean.TRUE.equals(request.prorateFirstYear()),
                request.seniorityStartYears(), request.seniorityIntervalYears(), request.seniorityAddDays(),
                request.seniorityMaxDays(), sortOrder, !Boolean.FALSE.equals(request.active()));
        LeaveTypeResponse created = get(user.companyId(), id);
        auditLogger.log(user, AuditAction.CREATE, "LEAVE_TYPE", id, null, created);
        return created;
    }

    @Transactional
    public LeaveTypeResponse update(LoginUser user, long leaveTypeId, LeaveTypeRequest request) {
        LeaveTypeResponse before = get(user.companyId(), leaveTypeId);
        validate(request);
        String name = request.name().trim();
        checkNameUnique(user.companyId(), name, leaveTypeId);
        jdbc.update("""
                        UPDATE leave_type SET name = ?, annual_days = ?, deducts_balance = ?, is_paid = ?,
                               prorate_first_year = ?, seniority_start_years = ?, seniority_interval_years = ?,
                               seniority_add_days = ?, seniority_max_days = ?, sort_order = ?, is_active = ?
                        WHERE id = ? AND company_id = ?
                        """,
                name, request.annualDays(), request.deductsBalance(), !Boolean.FALSE.equals(request.paid()),
                Boolean.TRUE.equals(request.prorateFirstYear()), request.seniorityStartYears(),
                request.seniorityIntervalYears(), request.seniorityAddDays(), request.seniorityMaxDays(),
                Objects.requireNonNullElse(request.sortOrder(), before.sortOrder()),
                Objects.requireNonNullElse(request.active(), before.active()), leaveTypeId, user.companyId());
        LeaveTypeResponse after = get(user.companyId(), leaveTypeId);
        auditLogger.log(user, AuditAction.UPDATE, "LEAVE_TYPE", leaveTypeId, before, after);
        return after;
    }

    /** 부여·신청 내역이 있으면 비활성화(DEACTIVATED), 없으면 삭제(DELETED). */
    @Transactional
    public DeleteResult delete(LoginUser user, long leaveTypeId) {
        LeaveTypeResponse before = get(user.companyId(), leaveTypeId);
        Boolean used = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM leave_grant WHERE company_id = ? AND leave_type_id = ?)
                            OR EXISTS (SELECT 1 FROM leave_request WHERE company_id = ? AND leave_type_id = ?)
                        """,
                Boolean.class, user.companyId(), leaveTypeId, user.companyId(), leaveTypeId);
        if (Boolean.TRUE.equals(used)) {
            jdbc.update("UPDATE leave_type SET is_active = FALSE WHERE id = ? AND company_id = ?",
                    leaveTypeId, user.companyId());
            auditLogger.log(user, AuditAction.UPDATE, "LEAVE_TYPE", leaveTypeId, before,
                    get(user.companyId(), leaveTypeId));
            return DeleteResult.deactivated();
        }
        jdbc.update("DELETE FROM leave_type WHERE id = ? AND company_id = ?", leaveTypeId, user.companyId());
        auditLogger.log(user, AuditAction.DELETE, "LEAVE_TYPE", leaveTypeId, before, null);
        return DeleteResult.deleted();
    }

    /** DB CHECK 전에 알기 쉬운 메시지로 막는다. */
    private static void validate(LeaveTypeRequest r) {
        long given = Stream.of(r.seniorityStartYears(), r.seniorityIntervalYears(), r.seniorityAddDays(),
                r.seniorityMaxDays()).filter(Objects::nonNull).count();
        if (given != 0 && given != 4) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "근속 가산 4개 값은 모두 입력하거나 모두 비워 두세요");
        }
        if (r.seniorityMaxDays() != null && r.seniorityMaxDays() < r.annualDays()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "최대 일수는 연간 부여일수 이상이어야 합니다");
        }
    }

    private void checkNameUnique(long companyId, String name, Long exceptId) {
        Boolean taken = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM leave_type WHERE company_id = ? AND name = ? AND id <> COALESCE(?, -1))
                        """,
                Boolean.class, companyId, name, exceptId);
        if (Boolean.TRUE.equals(taken)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
    }
}
