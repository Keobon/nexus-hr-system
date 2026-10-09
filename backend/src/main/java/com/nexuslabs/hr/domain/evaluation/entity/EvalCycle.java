package com.nexuslabs.hr.domain.evaluation.entity;

import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 평가 기간. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalCycle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "eval_cycle_status")
    private EvalCycleStatus status = EvalCycleStatus.SCHEDULED;

    private OffsetDateTime startedAt;
    private OffsetDateTime closedAt;

    public EvalCycle(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 예정이면 셋 다, 진행 중이면 종료일만 바뀐다 — 상태 검사는 EvalCycleService. */
    public void update(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void start(OffsetDateTime now) {
        this.status = EvalCycleStatus.IN_PROGRESS;
        this.startedAt = now;
    }

    public void close(OffsetDateTime now) {
        this.status = EvalCycleStatus.CLOSED;
        this.closedAt = now;
    }
}
