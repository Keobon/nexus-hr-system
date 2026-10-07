package com.nexuslabs.hr.domain.evaluation.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
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

import java.time.OffsetDateTime;

/** 평가 1건. 평가 시작 때 대상자·평가자·템플릿을 정해 저장한다. 점수는 저장하지 않는다(BR-EVAL-004). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_cycle_id")
    private EvalCycle evalCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_employee_id")
    private Employee targetEmployee;

    private long evaluatorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_template_id")
    private EvalTemplate evalTemplate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "evaluation_status")
    private EvaluationStatus status = EvaluationStatus.NOT_STARTED;

    private String overallComment;
    private OffsetDateTime submittedAt;
    private Long confirmedBy;
    private OffsetDateTime confirmedAt;
    private String reopenReason;

    public Evaluation(EvalCycle evalCycle, Employee targetEmployee, long evaluatorId, EvalTemplate evalTemplate) {
        this.evalCycle = evalCycle;
        this.targetEmployee = targetEmployee;
        this.evaluatorId = evaluatorId;
        this.evalTemplate = evalTemplate;
    }
}
