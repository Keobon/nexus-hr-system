package com.nexuslabs.hr.domain.evaluation.entity;

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

/** 평가 질문. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalQuestion extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_criteria_id")
    private EvalCriteria evalCriteria;

    private String content;
    private int sortOrder;

    public EvalQuestion(EvalCriteria evalCriteria, String content, int sortOrder) {
        this.evalCriteria = evalCriteria;
        this.content = content;
        this.sortOrder = sortOrder;
    }
}
