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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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

    /** PATCH 로 바꿀 수 있는 필드 = 등록 요청의 필드. */
    private static final Set<String> FIELDS = Arrays.stream(LeaveTypeRequest.class.getRecordComponents())
            .map(RecordComponent::getName).collect(Collectors.toUnmodifiableSet());
    /** 비울 수 없는 필드. null 을 보낼 수 있는 것은 근속 가산 4개뿐이다. */
    private static final Set<String> NOT_NULL = Set.of(
            "name", "annualDays", "deductsBalance", "paid", "prorateFirstYear", "sortOrder", "isActive");

    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public LeaveTypeService(JdbcTemplate jdbc, AuditLogger auditLogger, ObjectMapper objectMapper, Validator validator) {
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
        this.validator = validator;
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
                request.seniorityMaxDays(), sortOrder, !Boolean.FALSE.equals(request.isActive()));
        LeaveTypeResponse created = get(user.companyId(), id);
        auditLogger.log(user, AuditAction.CREATE, "LEAVE_TYPE", id, null, created);
        return created;
    }

    /**
     * API 설계서 1.1 PATCH — 보낸 필드만 바꾸고 null 은 비운다. 현재 값에 병합한 결과를 등록과 같은 규칙으로 검사한다.
     * 모르는 필드나 비울 수 없는 필드에 null 을 보내면 VALIDATION_ERROR(error.fields).
     */
    @Transactional
    public LeaveTypeResponse update(LoginUser user, long leaveTypeId, Map<String, Object> patch) {
        LeaveTypeResponse before = get(user.companyId(), leaveTypeId);
        LeaveTypeRequest request = merge(before, patch);
        validate(request);
        String name = request.name().trim();
        checkNameUnique(user.companyId(), name, leaveTypeId);
        jdbc.update("""
                        UPDATE leave_type SET name = ?, annual_days = ?, deducts_balance = ?, is_paid = ?,
                               prorate_first_year = ?, seniority_start_years = ?, seniority_interval_years = ?,
                               seniority_add_days = ?, seniority_max_days = ?, sort_order = ?, is_active = ?
                        WHERE id = ? AND company_id = ?
                        """,
                name, request.annualDays(), request.deductsBalance(), request.paid(), request.prorateFirstYear(),
                request.seniorityStartYears(), request.seniorityIntervalYears(), request.seniorityAddDays(),
                request.seniorityMaxDays(), request.sortOrder(), request.isActive(), leaveTypeId, user.companyId());
        LeaveTypeResponse after = get(user.companyId(), leaveTypeId);
        auditLogger.log(user, AuditAction.UPDATE, "LEAVE_TYPE", leaveTypeId, before, after);
        return after;
    }

    private LeaveTypeRequest merge(LeaveTypeResponse current, Map<String, Object> patch) {
        Map<String, String> errors = new LinkedHashMap<>();
        patch.forEach((field, value) -> {
            if (!FIELDS.contains(field)) {
                errors.put(field, "알 수 없는 항목입니다");
            } else if (value == null && NOT_NULL.contains(field)) {
                errors.put(field, "비울 수 없습니다");
            }
        });
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        LeaveTypeRequest base = new LeaveTypeRequest(current.name(), current.annualDays(), current.deductsBalance(),
                current.paid(), current.prorateFirstYear(), current.seniorityStartYears(),
                current.seniorityIntervalYears(), current.seniorityAddDays(), current.seniorityMaxDays(),
                current.sortOrder(), current.isActive());
        Map<String, Object> merged = new LinkedHashMap<>(objectMapper.convertValue(base, new TypeReference<Map<String, Object>>() {}));
        merged.putAll(patch);
        LeaveTypeRequest request;
        try {
            request = objectMapper.convertValue(merged, LeaveTypeRequest.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<ConstraintViolation<LeaveTypeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
            throw BusinessException.invalidFields(fields);
        }
        return request;
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
