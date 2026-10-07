package com.nexuslabs.hr.domain.employee.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** 직원 서류. 회사 서류와 같은 버전 방식이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeDocument extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "employee_doc_type")
    private EmployeeDocType docType;

    private String docName;
    private long fileId;
    private LocalDate issuedDate;
    private LocalDate expiresAt;
    private int versionNo;

    @Column(name = "is_current")
    private boolean current = true;

    private String memo;
    private long uploadedBy;

    public EmployeeDocument(Employee employee, EmployeeDocType docType, String docName, long fileId,
                            LocalDate issuedDate, LocalDate expiresAt, int versionNo, String memo, long uploadedBy) {
        this.employee = employee;
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
