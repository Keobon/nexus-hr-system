package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.dto.EmployeeFieldRequest;
import com.nexuslabs.hr.domain.employee.dto.EmployeeFieldResponse;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldDef;
import com.nexuslabs.hr.domain.employee.entity.FieldType;
import com.nexuslabs.hr.domain.employee.repository.EmployeeFieldDefRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 직원 추가 항목 정의(F-EMP-07, BR-EMP-005). 값이 있는 항목은 타입을 바꿀 수 없고, 지우면 비활성화(값은 남기고 숨김)한다.
 * 필수로 바꿔도 기존 직원의 빈 값은 그대로 두고 다음 수정부터 필수다. 감사 로그 대상이 아니다(BR-AUDIT-001 목록에 없음).
 */
@Service
public class EmployeeFieldService {

    private static final Set<String> FIELDS = Set.of("name", "fieldType", "options", "isRequired", "isMultiple",
            "isSelfEditable", "sortOrder", "isActive");
    private static final Set<String> NOT_NULL = Set.of("name", "fieldType", "isRequired", "isMultiple",
            "isSelfEditable", "sortOrder", "isActive");

    private final EmployeeFieldDefRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public EmployeeFieldService(EmployeeFieldDefRepository repository, JdbcTemplate jdbc, ObjectMapper objectMapper,
                                Validator validator) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** 입력 폼은 activeOnly=true, 설정 화면은 비활성까지(B-12 경비 종류와 같은 방식). */
    @Transactional(readOnly = true)
    public List<EmployeeFieldResponse> list(boolean activeOnly) {
        List<EmployeeFieldDef> defs = activeOnly ? repository.findByActiveTrueOrderBySortOrderAscIdAsc()
                : repository.findAllByOrderBySortOrderAscIdAsc();
        return defs.stream().map(EmployeeFieldResponse::from).toList();
    }

    @Transactional
    public EmployeeFieldResponse create(LoginUser user, EmployeeFieldRequest request) {
        String name = request.name().trim();
        List<String> options = options(request);
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : jdbc.queryForObject(
                "SELECT COALESCE(max(sort_order), 0) + 1 FROM employee_field_def WHERE company_id = ?",
                Integer.class, user.companyId());
        EmployeeFieldDef def = new EmployeeFieldDef(name, request.fieldType(), options,
                Boolean.TRUE.equals(request.required()), Boolean.TRUE.equals(request.multiple()),
                Boolean.TRUE.equals(request.selfEditable()), sortOrder);
        if (Boolean.FALSE.equals(request.active())) {
            def.deactivate();
        }
        return EmployeeFieldResponse.from(repository.save(def));
    }

    /**
     * 보낸 필드만 바꾼다. 값이 있는 항목의 타입 변경, 2건 이상 입력한 직원이 있는데 여러 건 허용을 끄는 것 → ITEM_IN_USE.
     * 선택지는 바꿀 수 있다 — 기존 값은 그대로 두고 다음 수정 때 검증한다.
     */
    @Transactional
    public EmployeeFieldResponse update(LoginUser user, long id, Map<String, Object> patch) {
        EmployeeFieldDef def = get(id);
        EmployeeFieldRequest request = merge(EmployeeFieldResponse.from(def), patch);
        String name = request.name().trim();
        List<String> options = options(request);
        if (request.fieldType() != def.getFieldType() && hasValues(user.companyId(), id)) {
            throw new BusinessException(ErrorCode.ITEM_IN_USE, "값이 입력된 항목은 타입을 바꿀 수 없습니다");
        }
        if (def.isMultiple() && !request.multiple() && hasMultipleValues(user.companyId(), id)) {
            throw new BusinessException(ErrorCode.ITEM_IN_USE, "여러 건을 입력한 직원이 있어 한 건으로 바꿀 수 없습니다");
        }
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        def.update(name, request.fieldType(), options, request.required(), request.multiple(), request.selfEditable(),
                request.sortOrder(), request.active());
        return EmployeeFieldResponse.from(def);
    }

    /** 값이 있으면 비활성화(DEACTIVATED), 없으면 삭제(DELETED). */
    @Transactional
    public DeleteResult delete(LoginUser user, long id) {
        EmployeeFieldDef def = get(id);
        if (hasValues(user.companyId(), id)) {
            def.deactivate();
            return DeleteResult.deactivated();
        }
        repository.delete(def);
        return DeleteResult.deleted();
    }

    private EmployeeFieldDef get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private boolean hasValues(long companyId, long defId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM employee_field_value WHERE company_id = ? AND field_def_id = ?)",
                Boolean.class, companyId, defId));
    }

    private boolean hasMultipleValues(long companyId, long defId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM employee_field_value WHERE company_id = ? AND field_def_id = ?
                                       GROUP BY employee_id HAVING count(*) > 1)
                        """,
                Boolean.class, companyId, defId));
    }

    /** SELECT 면 선택지 1개 이상 · 겹침 없음(앞뒤 공백 제거), 아니면 null. */
    private static List<String> options(EmployeeFieldRequest request) {
        if (request.fieldType() != FieldType.SELECT) {
            return null;
        }
        if (request.options() == null || request.options().isEmpty()) {
            throw BusinessException.invalidFields(Map.of("options", "선택지를 하나 이상 입력하세요"));
        }
        List<String> options = request.options().stream().map(String::trim).toList();
        if (new HashSet<>(options).size() != options.size()) {
            throw BusinessException.invalidFields(Map.of("options", "선택지가 겹칩니다"));
        }
        return options;
    }

    private EmployeeFieldRequest merge(EmployeeFieldResponse current, Map<String, Object> patch) {
        PatchRequest.check(patch, FIELDS, NOT_NULL);
        Object fieldType = patch.get("fieldType");
        if (fieldType != null && Arrays.stream(FieldType.values()).noneMatch(v -> v.name().equals(fieldType))) {
            throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE,
                    Map.of("allowed", Arrays.stream(FieldType.values()).map(Enum::name).toList()));
        }
        Map<String, Object> merged = new LinkedHashMap<>(
                objectMapper.convertValue(current, new TypeReference<Map<String, Object>>() {}));
        merged.remove("id");
        merged.putAll(patch);
        EmployeeFieldRequest request;
        try {
            request = objectMapper.convertValue(merged, EmployeeFieldRequest.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<ConstraintViolation<EmployeeFieldRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.forEach(cv -> fields.putIfAbsent(cv.getPropertyPath().toString(), cv.getMessage()));
            throw BusinessException.invalidFields(fields);
        }
        return request;
    }
}
