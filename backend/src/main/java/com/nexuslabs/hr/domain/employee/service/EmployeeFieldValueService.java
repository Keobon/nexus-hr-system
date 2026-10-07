package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.dto.FieldValueInput;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldDef;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldValue;
import com.nexuslabs.hr.domain.employee.repository.EmployeeFieldDefRepository;
import com.nexuslabs.hr.domain.employee.repository.EmployeeFieldValueRepository;
import com.nexuslabs.hr.global.error.BusinessException;
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
 * 직원 추가 항목 값(F-EMP-07 의 값 쪽). 값은 항목 정의의 타입대로 검증해 저장하고,
 * 여러 건 허용 항목은 순번(seq)으로 여러 값을 가진다(BR-EMP-005). 항목 정의 관리는 B-18 에서 만든다.
 */
@Service
public class EmployeeFieldValueService {

    private static final int TEXT_MAX_LENGTH = 255;

    private final EmployeeFieldDefRepository fieldDefRepository;
    private final EmployeeFieldValueRepository fieldValueRepository;

    public EmployeeFieldValueService(EmployeeFieldDefRepository fieldDefRepository,
                                     EmployeeFieldValueRepository fieldValueRepository) {
        this.fieldDefRepository = fieldDefRepository;
        this.fieldValueRepository = fieldValueRepository;
    }

    /**
     * 새로 등록한 직원의 추가 항목 값을 저장한다. 활성 항목만 받고, 필수 항목은 값이 하나 이상 있어야 한다.
     * 잘못된 값은 항목마다 모아 VALIDATION_ERROR(error.fields 의 "fieldValues.{항목 ID}")로 알린다.
     */
    @Transactional
    public void saveForNewEmployee(Employee employee, List<FieldValueInput> inputs) {
        Map<Long, EmployeeFieldDef> defs = fieldDefRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
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
                errors.put("fieldValues." + defId, error);
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
                .forEach(def -> errors.put("fieldValues." + def.getId(), def.getName() + "은(는) 필수 항목입니다"));
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        fieldValueRepository.saveAll(values);
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
