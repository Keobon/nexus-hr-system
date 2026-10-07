package com.nexuslabs.hr.domain.employee.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/** 직원 추가 항목 정의. SELECT 형식이면 options 에 선택지를 둔다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeFieldDef extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "field_type")
    private FieldType fieldType;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> options;

    @Column(name = "is_required")
    private boolean required;

    @Column(name = "is_multiple")
    private boolean multiple;

    @Column(name = "is_self_editable")
    private boolean selfEditable;

    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;

    public EmployeeFieldDef(String name, FieldType fieldType, List<String> options, boolean required, boolean multiple,
                            boolean selfEditable, int sortOrder) {
        this.name = name;
        this.fieldType = fieldType;
        this.options = options;
        this.required = required;
        this.multiple = multiple;
        this.selfEditable = selfEditable;
        this.sortOrder = sortOrder;
    }
}
