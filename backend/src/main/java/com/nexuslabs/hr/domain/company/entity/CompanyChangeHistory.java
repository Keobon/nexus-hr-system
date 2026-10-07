package com.nexuslabs.hr.domain.company.entity;

import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** 회사 정보 변경 이력(BR-TEN-004). append-only. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyChangeHistory extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "company_field")
    private CompanyField field;

    private String oldValue;
    private String newValue;
    private LocalDate effectiveDate;
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private CompanyDocument document;

    private long changedBy;

    public CompanyChangeHistory(CompanyField field, String oldValue, String newValue, LocalDate effectiveDate,
                                String reason, CompanyDocument document, long changedBy) {
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.document = document;
        this.changedBy = changedBy;
    }
}
