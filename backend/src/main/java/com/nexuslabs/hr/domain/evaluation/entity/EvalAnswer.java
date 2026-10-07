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

/** 질문별 점수(1–5). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalAnswer extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id")
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_question_id")
    private EvalQuestion evalQuestion;

    private short score;

    public EvalAnswer(Evaluation evaluation, EvalQuestion evalQuestion, short score) {
        this.evaluation = evaluation;
        this.evalQuestion = evalQuestion;
        this.score = score;
    }
}
