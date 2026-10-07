package com.nexuslabs.hr.domain.company.entity;

import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalTime;

/** 근무시간 이력(BR-WORK-001). 수정하지 않고 새 적용 시작일로 행을 쌓는다. 시작 전 행만 삭제할 수 있다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkSchedule extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate effectiveFrom;
    private LocalTime startTime;
    private LocalTime endTime;
    private short breakMinutes;
    private short lateGraceMinutes;
    private LocalTime nightStart;
    private LocalTime nightEnd;
    private boolean overtimeApprovalRequired;
    private short workDays;
    private Long createdBy;

    public WorkSchedule(LocalDate effectiveFrom, LocalTime startTime, LocalTime endTime, short breakMinutes,
                        short lateGraceMinutes, LocalTime nightStart, LocalTime nightEnd,
                        boolean overtimeApprovalRequired, short workDays, Long createdBy) {
        this.effectiveFrom = effectiveFrom;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.lateGraceMinutes = lateGraceMinutes;
        this.nightStart = nightStart;
        this.nightEnd = nightEnd;
        this.overtimeApprovalRequired = overtimeApprovalRequired;
        this.workDays = workDays;
        this.createdBy = createdBy;
    }
}
