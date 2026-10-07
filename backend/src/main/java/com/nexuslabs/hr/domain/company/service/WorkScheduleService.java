package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.company.dto.Weekday;
import com.nexuslabs.hr.domain.company.dto.WorkScheduleList;
import com.nexuslabs.hr.domain.company.dto.WorkScheduleRequest;
import com.nexuslabs.hr.domain.company.dto.WorkScheduleResponse;
import com.nexuslabs.hr.domain.company.entity.WorkSchedule;
import com.nexuslabs.hr.domain.company.repository.WorkScheduleRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * 근무시간 설정(F-COMP-04, BR-WORK-001). 수정하지 않고 새 적용 시작일로 행을 쌓는다 —
 * 그래야 근무시간을 바꿔도 과거 근태에는 이전 기준이 적용된다. 아직 시작하지 않은 행만 지울 수 있다(DB 트리거도 막는다).
 */
@Service
public class WorkScheduleService {

    private static final LocalTime DEFAULT_NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_NIGHT_END = LocalTime.of(6, 0);

    private final WorkScheduleRepository workScheduleRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public WorkScheduleService(WorkScheduleRepository workScheduleRepository, AuditLogger auditLogger, Clock clock) {
        this.workScheduleRepository = workScheduleRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkScheduleList list() {
        LocalDate today = LocalDate.now(clock);
        List<WorkSchedule> all = workScheduleRepository.findAllByOrderByEffectiveFromDesc();
        List<WorkSchedule> started = all.stream().filter(s -> !s.getEffectiveFrom().isAfter(today)).toList();
        List<WorkScheduleResponse> upcoming = all.stream().filter(s -> s.getEffectiveFrom().isAfter(today))
                .sorted(Comparator.comparing(WorkSchedule::getEffectiveFrom))
                .map(WorkScheduleResponse::from).toList();
        return new WorkScheduleList(
                started.isEmpty() ? null : WorkScheduleResponse.from(started.get(0)),
                upcoming,
                started.stream().skip(1).map(WorkScheduleResponse::from).toList());
    }

    @Transactional
    public WorkScheduleResponse create(LoginUser user, WorkScheduleRequest request) {
        LocalTime nightStart = request.nightStart() != null ? request.nightStart() : DEFAULT_NIGHT_START;
        LocalTime nightEnd = request.nightEnd() != null ? request.nightEnd() : DEFAULT_NIGHT_END;
        if (!request.startTime().isBefore(request.endTime())) {
            throw BusinessException.invalidFields(Map.of("endTime", "퇴근 시각은 출근 시각보다 늦어야 합니다"));
        }
        if (request.breakMinutes() >= Duration.between(request.startTime(), request.endTime()).toMinutes()) {
            throw BusinessException.invalidFields(Map.of("breakMinutes", "휴게시간은 근무시간보다 짧아야 합니다"));
        }
        if (nightStart.equals(nightEnd)) {
            throw BusinessException.invalidFields(Map.of("nightEnd", "야간 시간대의 시작과 끝이 같을 수 없습니다"));
        }
        if (request.effectiveFrom().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.SCHEDULE_PAST_DATE);
        }
        if (workScheduleRepository.existsByEffectiveFrom(request.effectiveFrom())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "같은 적용 시작일의 근무시간이 이미 있습니다");
        }

        WorkSchedule schedule = workScheduleRepository.save(new WorkSchedule(request.effectiveFrom(),
                request.startTime(), request.endTime(), request.breakMinutes().shortValue(),
                request.lateGraceMinutes().shortValue(), nightStart, nightEnd,
                !Boolean.FALSE.equals(request.overtimeApprovalRequired()),
                Weekday.toBits(EnumSet.copyOf(request.workDays())), user.employeeId()));
        WorkScheduleResponse created = WorkScheduleResponse.from(schedule);
        auditLogger.log(user, AuditAction.CREATE, "WORK_SCHEDULE", schedule.getId(), null, created);
        return created;
    }

    /** 아직 시작하지 않은(적용 시작일이 내일 이후인) 행만 지운다. */
    @Transactional
    public void delete(LoginUser user, long workScheduleId) {
        WorkSchedule schedule = workScheduleRepository.findById(workScheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!schedule.getEffectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 시작된 근무시간은 삭제할 수 없습니다");
        }
        WorkScheduleResponse before = WorkScheduleResponse.from(schedule);
        workScheduleRepository.delete(schedule);
        auditLogger.log(user, AuditAction.DELETE, "WORK_SCHEDULE", workScheduleId, before, null);
    }
}
