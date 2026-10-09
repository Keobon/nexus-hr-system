package com.nexuslabs.hr.domain.evaluation.entity;

import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

/** 평가 템플릿. 평가 기간에 쓰인 템플릿은 수정하지 않고 복사해서 새로 만든다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copied_from_id")
    private EvalTemplate copiedFrom;

    @Column(name = "is_active")
    private boolean active = true;

    @OneToMany(mappedBy = "evalTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<EvalCriteria> criteria = new ArrayList<>();

    public EvalTemplate(String name, EvalTemplate copiedFrom) {
        this.name = name;
        this.copiedFrom = copiedFrom;
    }

    public void addCriteria(EvalCriteria item) {
        criteria.add(item);
    }

    /** 통째로 다시 저장(PUT) — 쓰이지 않은 템플릿만. 기존 항목 · 질문은 orphanRemoval 로 지워진다. */
    public void rename(String name) {
        this.name = name;
    }

    public void clearCriteria() {
        criteria.clear();
    }

    /** 쓰인 템플릿 삭제 = 비활성화(BR-ORG-001). */
    public void deactivate() {
        this.active = false;
    }
}
