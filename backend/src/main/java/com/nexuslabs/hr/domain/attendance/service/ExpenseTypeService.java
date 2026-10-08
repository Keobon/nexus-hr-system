package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.dto.ExpenseTypeRequest;
import com.nexuslabs.hr.domain.attendance.dto.ExpenseTypeResponse;
import com.nexuslabs.hr.domain.attendance.entity.ExpenseType;
import com.nexuslabs.hr.domain.attendance.repository.ExpenseTypeRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import com.nexuslabs.hr.global.response.DeleteResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 출장 경비 종류 관리(F-ATT-09). 청구에 쓰인 종류는 지우지 않고 비활성화한다.
 * 등록 · 수정 · 삭제는 감사 로그를 남긴다(BR-AUDIT-001).
 */
@Service
public class ExpenseTypeService {

    private static final String AUDIT_TARGET = "EXPENSE_TYPE";
    private static final int NAME_MAX = 50;
    private static final Set<String> PATCH_FIELDS = Set.of("name", "receiptRequired", "sortOrder", "isActive");

    private final ExpenseTypeRepository repository;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;

    public ExpenseTypeService(ExpenseTypeRepository repository, JdbcTemplate jdbc, AuditLogger auditLogger) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<ExpenseTypeResponse> list(boolean activeOnly) {
        List<ExpenseType> types = activeOnly ? repository.findByActiveTrueOrderBySortOrderAscIdAsc()
                : repository.findAllByOrderBySortOrderAscIdAsc();
        return types.stream().map(ExpenseTypeResponse::from).toList();
    }

    @Transactional
    public ExpenseTypeResponse create(LoginUser user, ExpenseTypeRequest request) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : jdbc.queryForObject(
                "SELECT COALESCE(max(sort_order), 0) + 1 FROM expense_type WHERE company_id = ?",
                Integer.class, user.companyId());
        ExpenseType type = new ExpenseType(name, !Boolean.FALSE.equals(request.receiptRequired()), sortOrder);
        if (Boolean.FALSE.equals(request.active())) {
            type.deactivate();
        }
        ExpenseTypeResponse created = ExpenseTypeResponse.from(repository.save(type));
        auditLogger.log(user, AuditAction.CREATE, AUDIT_TARGET, created.id(), null, created);
        return created;
    }

    /** API 설계서 1.1 PATCH — 보낸 필드만 바꾼다. 네 필드 모두 비울 수 없다. */
    @Transactional
    public ExpenseTypeResponse update(LoginUser user, long id, Map<String, Object> patch) {
        PatchRequest.check(patch, PATCH_FIELDS, PATCH_FIELDS);
        ExpenseType type = get(id);
        ExpenseTypeResponse before = ExpenseTypeResponse.from(type);

        Map<String, String> errors = new LinkedHashMap<>();
        String name = patch.containsKey("name") ? parseName(patch.get("name"), errors) : type.getName();
        boolean receiptRequired = patch.containsKey("receiptRequired")
                ? parseBoolean("receiptRequired", patch.get("receiptRequired"), errors) : type.isReceiptRequired();
        int sortOrder = patch.containsKey("sortOrder") ? parseInt("sortOrder", patch.get("sortOrder"), errors)
                : type.getSortOrder();
        boolean active = patch.containsKey("isActive") ? parseBoolean("isActive", patch.get("isActive"), errors)
                : type.isActive();
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        type.update(name, receiptRequired, sortOrder, active);
        ExpenseTypeResponse after = ExpenseTypeResponse.from(type);
        auditLogger.log(user, AuditAction.UPDATE, AUDIT_TARGET, id, before, after);
        return after;
    }

    /** 경비 청구에 쓰인 적 있으면 비활성화(DEACTIVATED), 없으면 삭제(DELETED). */
    @Transactional
    public DeleteResult delete(LoginUser user, long id) {
        ExpenseType type = get(id);
        ExpenseTypeResponse before = ExpenseTypeResponse.from(type);
        Boolean used = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM expense_claim_line WHERE company_id = ? AND expense_type_id = ?)",
                Boolean.class, user.companyId(), id);
        if (Boolean.TRUE.equals(used)) {
            type.deactivate();
            auditLogger.log(user, AuditAction.UPDATE, AUDIT_TARGET, id, before, ExpenseTypeResponse.from(type));
            return DeleteResult.deactivated();
        }
        repository.delete(type);
        auditLogger.log(user, AuditAction.DELETE, AUDIT_TARGET, id, before, null);
        return DeleteResult.deleted();
    }

    /** 경비 청구가 종류를 고를 때 쓴다. 없으면 NOT_FOUND, 비활성이면 INACTIVE_REFERENCE. */
    @Transactional(readOnly = true)
    public ExpenseType requireActive(long id) {
        ExpenseType type = get(id);
        if (!type.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        return type;
    }

    private ExpenseType get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static String parseName(Object value, Map<String, String> errors) {
        if (!(value instanceof String s) || s.isBlank()) {
            errors.put("name", "이름을 입력하세요");
            return null;
        }
        if (s.trim().length() > NAME_MAX) {
            errors.put("name", NAME_MAX + "자 이하로 입력하세요");
            return null;
        }
        return s.trim();
    }

    private static boolean parseBoolean(String field, Object value, Map<String, String> errors) {
        if (value instanceof Boolean b) {
            return b;
        }
        errors.put(field, "true 또는 false 여야 합니다");
        return false;
    }

    private static int parseInt(String field, Object value, Map<String, String> errors) {
        if (value instanceof Integer n) {
            return n;
        }
        errors.put(field, "정수여야 합니다");
        return 0;
    }
}
