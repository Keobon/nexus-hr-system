package com.nexuslabs.hr.domain.employee.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFamily;
import com.nexuslabs.hr.domain.employee.entity.FamilyRelation;

import java.time.LocalDate;

/** 가족 한 명(F-EMP-09). 장애인 · 동거 여부는 기록용이다(계산에 쓰지 않음). */
public record FamilyMember(long id, String name, FamilyRelation relation, LocalDate birthDate,
                           @JsonProperty("isTaxDependent") boolean taxDependent,
                           @JsonProperty("isDisabled") boolean disabled,
                           @JsonProperty("isCohabiting") boolean cohabiting) {

    public static FamilyMember from(EmployeeFamily family) {
        return new FamilyMember(family.getId(), family.getName(), family.getRelation(), family.getBirthDate(),
                family.isTaxDependent(), family.isDisabled(), family.isCohabiting());
    }
}
