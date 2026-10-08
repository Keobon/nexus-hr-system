package com.nexuslabs.hr.domain.employee.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldDef;
import com.nexuslabs.hr.domain.employee.entity.FieldType;

import java.util.List;

/** 직원 추가 항목 정의 한 건(API 설계서 6장). options 는 SELECT 가 아니면 null. */
public record EmployeeFieldResponse(long id, String name, FieldType fieldType, List<String> options,
                                    @JsonProperty("isRequired") boolean required,
                                    @JsonProperty("isMultiple") boolean multiple,
                                    @JsonProperty("isSelfEditable") boolean selfEditable,
                                    int sortOrder,
                                    @JsonProperty("isActive") boolean active) {

    public static EmployeeFieldResponse from(EmployeeFieldDef def) {
        return new EmployeeFieldResponse(def.getId(), def.getName(), def.getFieldType(),
                def.getOptions() == null ? null : List.copyOf(def.getOptions()), def.isRequired(), def.isMultiple(),
                def.isSelfEditable(), def.getSortOrder(), def.isActive());
    }
}
