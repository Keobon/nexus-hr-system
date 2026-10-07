package com.nexuslabs.hr.domain.evaluation.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
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

import java.util.ArrayList;
import java.util.List;

/** 평가 항목. 템플릿 안에서 가중치 합이 100이어야 한다(서비스 검증). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalCriteria extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_template_id")
    private EvalTemplate evalTemplate;

    private String category;
    private String name;
    private short weight;
    private int sortOrder;

    @OneToMany(mappedBy = "evalCriteria", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<EvalQuestion> questions = new ArrayList<>();

    public EvalCriteria(EvalTemplate evalTemplate, String category, String name, short weight, int sortOrder) {
        this.evalTemplate = evalTemplate;
        this.category = category;
        this.name = name;
        this.weight = weight;
        this.sortOrder = sortOrder;
    }

    public void addQuestion(EvalQuestion question) {
        questions.add(question);
    }
}
