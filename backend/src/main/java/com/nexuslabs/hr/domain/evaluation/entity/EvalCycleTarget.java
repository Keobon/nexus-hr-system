package com.nexuslabs.hr.domain.evaluation.entity;

import com.nexuslabs.hr.domain.org.entity.EmploymentType;
import com.nexuslabs.hr.domain.org.entity.JobGrade;
import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 평가 대상 규칙(BR-EVAL-003). 조건이 모두 없으면 "모두"이고, evalTemplate 이 없으면 평가 제외다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalCycleTarget extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_cycle_id")
    private EvalCycle evalCycle;

    private int priority;
    private Boolean condIsOrgLead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cond_job_title_id")
    private JobTitle condJobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cond_job_grade_id")
    private JobGrade condJobGrade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cond_employment_type_id")
    private EmploymentType condEmploymentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_template_id")
    private EvalTemplate evalTemplate;

    public EvalCycleTarget(EvalCycle evalCycle, int priority, Boolean condIsOrgLead, JobTitle condJobTitle,
                           JobGrade condJobGrade, EmploymentType condEmploymentType, EvalTemplate evalTemplate) {
        this.evalCycle = evalCycle;
        this.priority = priority;
        this.condIsOrgLead = condIsOrgLead;
        this.condJobTitle = condJobTitle;
        this.condJobGrade = condJobGrade;
        this.condEmploymentType = condEmploymentType;
        this.evalTemplate = evalTemplate;
    }
}
