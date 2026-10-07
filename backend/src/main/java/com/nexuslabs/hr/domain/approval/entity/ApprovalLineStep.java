package com.nexuslabs.hr.domain.approval.entity;

import com.nexuslabs.hr.domain.approval.service.ApproverType;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.global.entity.TenantEntity;
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

/** 승인선 단계 정의. 승인자 유형에 따라 upLevels·jobTitle·employee 중 하나를 쓴다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalLineStep extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_line_id")
    private ApprovalLine approvalLine;

    private short stepOrder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "approver_type")
    private ApproverType approverType;

    private Short upLevels;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    public ApprovalLineStep(ApprovalLine approvalLine, short stepOrder, ApproverType approverType, Short upLevels,
                            JobTitle jobTitle, Employee employee) {
        this.approvalLine = approvalLine;
        this.stepOrder = stepOrder;
        this.approverType = approverType;
        this.upLevels = upLevels;
        this.jobTitle = jobTitle;
        this.employee = employee;
    }
}
