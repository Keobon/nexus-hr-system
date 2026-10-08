package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.dto.FieldValueInput;
import com.nexuslabs.hr.domain.employee.dto.MyProfileResponse;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldDef;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldValue;
import com.nexuslabs.hr.domain.employee.entity.FieldType;
import com.nexuslabs.hr.domain.employee.repository.EmployeeFieldDefRepository;
import com.nexuslabs.hr.domain.employee.repository.EmployeeFieldValueRepository;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 직원 추가 항목 값(F-EMP-07 의 값 쪽, F-EMP-03). 값은 항목 정의의 타입대로 검증해 저장하고,
 * 여러 건 허용 항목은 순번(seq)으로 여러 값을 가진다(BR-EMP-005). 항목 정의 관리는 EmployeeFieldService.
 * 비활성 항목의 값은 남기고 화면에서 숨긴다 — 조회 · 교체 모두 활성 항목만 다룬다.
 */
@Service
public class EmployeeFieldValueService {

    private static final int TEXT_MAX_LENGTH = 255;

    private final EmployeeFieldDefRepository fieldDefRepository;
    private final EmployeeFieldValueRepository fieldValueRepository;
    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate jdbc;

    public EmployeeFieldValueService(EmployeeFieldDefRepository fieldDefRepository,
                                     EmployeeFieldValueRepository fieldValueRepository,
                                     EmployeeRepository employeeRepository, JdbcTemplate jdbc) {
        this.fieldDefRepository = fieldDefRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.employeeRepository = employeeRepository;
        this.jdbc = jdbc;
    }

    /**
     * 새로 등록한 직원의 추가 항목 값을 저장한다. 활성 항목만 받고, 필수 항목은 값이 하나 이상 있어야 한다.
     * 잘못된 값은 항목마다 모아 VALIDATION_ERROR(error.fields 의 "fieldValues.{항목 ID}")로 알린다.
     */
    @Transactional
    public void saveForNewEmployee(Employee employee, List<FieldValueInput> inputs) {
        List<EmployeeFieldDef> active = fieldDefRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        fieldValueRepository.saveAll(build(employee, inputs, active, "fieldValues."));
    }

    /**
     * 관리자 교체(EMPLOYEE_MANAGE) — 활성 항목 전체를 보낸 값으로 바꾼다. 필수 검사는 이때 적용한다(필수로 바꾼 뒤 다음 수정부터).
     * 퇴직자 → EMPLOYEE_RESIGNED. 잘못된 값은 error.fields 의 "values.{항목 ID}".
     */
    @Transactional
    public List<MyProfileResponse.FieldValue> replace(LoginUser user, long employeeId, List<FieldValueInput> inputs) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (employee.getStatus() == EmpStatus.RESIGNED) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        return replace(user.companyId(), employee, inputs, fieldDefRepository.findByActiveTrueOrderBySortOrderAscIdAsc());
    }

    /** 본인 교체 — 본인 수정 가능 항목만 바꾼다. 다른 활성 항목이 섞이면 FORBIDDEN. */
    @Transactional
    public List<MyProfileResponse.FieldValue> replaceMine(LoginUser user, List<FieldValueInput> inputs) {
        List<EmployeeFieldDef> active = fieldDefRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        Set<Long> locked = active.stream().filter(def -> !def.isSelfEditable()).map(EmployeeFieldDef::getId)
                .collect(Collectors.toSet());
        if (inputs.stream().anyMatch(input -> locked.contains(input.fieldDefId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 수정할 수 없는 항목이 있습니다");
        }
        Employee employee = employeeRepository.findById(user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return replace(user.companyId(), employee, inputs,
                active.stream().filter(EmployeeFieldDef::isSelfEditable).toList());
    }

    /** 한 직원의 활성 항목 값 — 정렬 순서 · 순번 순. 개인 페이지와 직원 상세(전사 범위)가 같은 모양을 쓴다. */
    @Transactional(readOnly = true)
    public List<MyProfileResponse.FieldValue> values(long companyId, long employeeId) {
        return jdbc.query("""
                        SELECT v.field_def_id, d.name, d.field_type::text AS field_type, v.seq, v.value
                        FROM employee_field_value v
                             JOIN employee_field_def d ON d.id = v.field_def_id AND d.company_id = v.company_id
                        WHERE v.company_id = ? AND v.employee_id = ? AND d.is_active
                        ORDER BY d.sort_order, d.id, v.seq
                        """,
                (rs, i) -> new MyProfileResponse.FieldValue(rs.getLong("field_def_id"), rs.getString("name"),
                        FieldType.valueOf(rs.getString("field_type")), rs.getInt("seq"), rs.getString("value")),
                companyId, employeeId);
    }

    private List<MyProfileResponse.FieldValue> replace(long companyId, Employee employee, List<FieldValueInput> inputs,
                                                       List<EmployeeFieldDef> targets) {
        List<EmployeeFieldValue> values = build(employee, inputs, targets, "values.");
        if (!targets.isEmpty()) {
            jdbc.update("DELETE FROM employee_field_value WHERE company_id = ? AND employee_id = ? AND field_def_id = ANY(?)",
                    companyId, employee.getId(), targets.stream().map(EmployeeFieldDef::getId).toArray(Long[]::new));
        }
        fieldValueRepository.saveAllAndFlush(values);
        return values(companyId, employee.getId());
    }

    /** targets 에 없는 항목, 타입이 맞지 않는 값, 값이 없는 필수 항목을 모아 VALIDATION_ERROR. */
    private static List<EmployeeFieldValue> build(Employee employee, List<FieldValueInput> inputs,
                                                  List<EmployeeFieldDef> targets, String errorPrefix) {
        Map<Long, EmployeeFieldDef> defs = targets.stream()
                .collect(Collectors.toMap(EmployeeFieldDef::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<FieldValueInput>> byDef = new LinkedHashMap<>();
        for (FieldValueInput input : inputs == null ? List.<FieldValueInput>of() : inputs) {
            byDef.computeIfAbsent(input.fieldDefId(), k -> new ArrayList<>()).add(input);
        }

        Map<String, String> errors = new LinkedHashMap<>();
        List<EmployeeFieldValue> values = new ArrayList<>();
        byDef.forEach((defId, group) -> {
            EmployeeFieldDef def = defs.get(defId);
            String error = def == null ? "없거나 사용 중지된 항목입니다" : check(def, group);
            if (error != null) {
                errors.put(errorPrefix + defId, error);
                return;
            }
            for (int i = 0; i < group.size(); i++) {
                FieldValueInput input = group.get(i);
                int seq = input.seq() != null ? input.seq() : i + 1;
                values.add(new EmployeeFieldValue(employee, def, (short) seq, input.value().trim()));
            }
        });
        defs.values().stream()
                .filter(def -> def.isRequired() && !byDef.containsKey(def.getId()))
                .forEach(def -> errors.put(errorPrefix + def.getId(), def.getName() + "은(는) 필수 항목입니다"));
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        return values;
    }

    /** 문제가 없으면 null. */
    private static String check(EmployeeFieldDef def, List<FieldValueInput> group) {
        if (!def.isMultiple() && group.size() > 1) {
            return def.getName() + "은(는) 한 건만 입력할 수 있습니다";
        }
        Set<Integer> seqs = new HashSet<>();
        for (int i = 0; i < group.size(); i++) {
            FieldValueInput input = group.get(i);
            if (!seqs.add(input.seq() != null ? input.seq() : i + 1)) {
                return def.getName() + "의 순번이 겹칩니다";
            }
            String error = checkType(def, input.value().trim());
            if (error != null) {
                return def.getName() + ": " + error;
            }
        }
        return null;
    }

    private static String checkType(EmployeeFieldDef def, String value) {
        switch (def.getFieldType()) {
            case TEXT -> {
                if (value.length() > TEXT_MAX_LENGTH) {
                    return TEXT_MAX_LENGTH + "자 이하로 입력하세요";
                }
            }
            case NUMBER -> {
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException e) {
                    return "숫자로 입력하세요";
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(value);
                } catch (DateTimeParseException e) {
                    return "날짜 형식(2026-10-01)으로 입력하세요";
                }
            }
            case SELECT -> {
                if (def.getOptions() == null || !def.getOptions().contains(value)) {
                    return "선택지에 없는 값입니다";
                }
            }
            case LONG_TEXT -> {
                // 길이 제한 없음
            }
        }
        return null;
    }
}
