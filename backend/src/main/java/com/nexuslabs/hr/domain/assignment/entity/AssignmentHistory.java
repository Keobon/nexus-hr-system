package com.nexuslabs.hr.domain.assignment.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.org.entity.JobGrade;
import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.domain.org.entity.OrgUnit;
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

/** 발령 이력(BR-ASSIGN-001–004). append-only. 정정은 원본을 가리키는 새 행으로 남긴다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssignmentHistory extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "assignment_type")
    private AssignmentType assignmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_org_unit_id")
    private OrgUnit fromOrgUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_org_unit_id")
    private OrgUnit toOrgUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_job_grade_id")
    private JobGrade fromJobGrade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_job_grade_id")
    private JobGrade toJobGrade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_job_title_id")
    private JobTitle fromJobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_job_title_id")
    private JobTitle toJobTitle;

    private String reason;
    private LocalDate effectiveDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_of_id")
    private AssignmentHistory correctionOf;

    private long createdBy;

    public AssignmentHistory(Employee employee, AssignmentType assignmentType, OrgUnit fromOrgUnit, OrgUnit toOrgUnit,
                             JobGrade fromJobGrade, JobGrade toJobGrade, JobTitle fromJobTitle, JobTitle toJobTitle,
                             String reason, LocalDate effectiveDate, AssignmentHistory correctionOf, long createdBy) {
        this.employee = employee;
        this.assignmentType = assignmentType;
        this.fromOrgUnit = fromOrgUnit;
        this.toOrgUnit = toOrgUnit;
        this.fromJobGrade = fromJobGrade;
        this.toJobGrade = toJobGrade;
        this.fromJobTitle = fromJobTitle;
        this.toJobTitle = toJobTitle;
        this.reason = reason;
        this.effectiveDate = effectiveDate;
        this.correctionOf = correctionOf;
        this.createdBy = createdBy;
    }
}
