package com.nexuslabs.hr.domain.company.entity;

import com.nexuslabs.hr.global.entity.CreatedAtEntity;
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

import java.time.LocalDate;

/** 회사 서류. 같은 종류를 다시 올리면 새 행(버전)이 생기고 이전 행은 current=false 가 된다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyDocument extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "company_doc_type")
    private CompanyDocType docType;

    private String docName;
    private long fileId;
    private LocalDate issuedDate;
    private LocalDate expiresAt;
    private int versionNo;

    @Column(name = "is_current")
    private boolean current = true;

    private String memo;
    private long uploadedBy;

    public CompanyDocument(CompanyDocType docType, String docName, long fileId, LocalDate issuedDate,
                           LocalDate expiresAt, int versionNo, String memo, long uploadedBy) {
        this.docType = docType;
        this.docName = docName;
        this.fileId = fileId;
        this.issuedDate = issuedDate;
        this.expiresAt = expiresAt;
        this.versionNo = versionNo;
        this.memo = memo;
        this.uploadedBy = uploadedBy;
    }
}
