package com.nexuslabs.hr.domain.approval.entity;

import com.nexuslabs.hr.domain.account.entity.Role;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** 승인선(BR-APPR-001). 기본 승인선은 조건이 없고 항상 활성이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalLine extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "approval_work_type")
    private ApprovalWorkType workType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cond_job_title_id")
    private JobTitle condJobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cond_role_id")
    private Role condRole;

    @Column(name = "is_default")
    private boolean defaultLine;

    private int priority;

    @Column(name = "is_active")
    private boolean active = true;

    @OneToMany(mappedBy = "approvalLine", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder")
    private List<ApprovalLineStep> steps = new ArrayList<>();

    public ApprovalLine(String name, ApprovalWorkType workType, JobTitle condJobTitle, Role condRole,
                        boolean defaultLine, int priority) {
        this.name = name;
        this.workType = workType;
        this.condJobTitle = condJobTitle;
        this.condRole = condRole;
        this.defaultLine = defaultLine;
        this.priority = priority;
    }

    public void addStep(ApprovalLineStep step) {
        steps.add(step);
    }
}
