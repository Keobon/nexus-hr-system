package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceCorrectionResponse;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceDay;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceExplanationRequest;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceExplanationResponse;
import com.nexuslabs.hr.domain.attendance.entity.AttendanceExplanation;
import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.attendance.repository.AttendanceExplanationRepository;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nexuslabs.hr.domain.attendance.service.RequestAccess.seoul;

/**
 * 근태 소명(F-ATT-10). 직원이 지각 · 조퇴 · 퇴근미기록 · 결근인 날에 사유와 실제 시각(선택)을 내고,
 * ATTENDANCE_MANAGE 가 승인하면 {@link AttendanceCorrectionService} 의 정정 규칙 그대로 근태를 고친다(기록 방식 ADMIN · 감사 로그).
 * 정정 대상 목록에 소명 대기를 넣는 쿼리는 순환 의존을 피하려고 정정 서비스 쪽에 있다.
 */
@Service
public class AttendanceExplanationService {

    private static final Set<WorkType> SELECTABLE_WORK_TYPES = Set.of(WorkType.OFFICE, WorkType.REMOTE, WorkType.FIELD);
    /** 그날 계산 결과가 이 상태면 소명할 수 있다(지각 · 조퇴는 출퇴근 기록이 있어도). */
    private static final Set<AttendanceDayStatus> EXPLAINABLE = Set.of(AttendanceDayStatus.MISSING_CHECKOUT,
            AttendanceDayStatus.CHECKED_IN, AttendanceDayStatus.ABSENT);
    private static final String NOT_EXPLAINABLE = "소명할 근태가 아닙니다";
    /** 한 직원 · 한 날짜에 대기 중인 소명 1건(schema.sql 부분 유일 인덱스). */
    private static final String PENDING_UNIQUE = "ux_attendance_explanation_pending";

    private final AttendanceExplanationRepository repository;
    private final AttendanceCorrectionService correctionService;
    private final AttendanceCalculator calculator;
    private final RequestAccess access;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public AttendanceExplanationService(AttendanceExplanationRepository repository,
                                        AttendanceCorrectionService correctionService, AttendanceCalculator calculator,
                                        RequestAccess access, JdbcTemplate jdbc, EntityManager em, Clock clock) {
        this.repository = repository;
        this.correctionService = correctionService;
        this.calculator = calculator;
        this.access = access;
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    /**
     * 신청. 어제까지의 근무일 중 지각 · 조퇴 · 퇴근미기록 · 결근인 날만, 그 달 급여가 확정되기 전까지.
     * 한 날짜에 대기 중인 소명은 1건 — 동시에 두 번 보내면 부분 유일 인덱스가 막는다.
     */
    @Transactional
    public AttendanceExplanationResponse create(LoginUser user, AttendanceExplanationRequest request) {
        long cid = user.companyId();
        long employeeId = user.employeeId();
        access.requireActive(cid, employeeId);
        LocalDate workDate = request.workDate();
        if (!workDate.isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, NOT_EXPLAINABLE);
        }
        AttendanceDay day = calculator.month(cid, employeeId, YearMonth.from(workDate)).days().stream()
                .filter(d -> d.date().equals(workDate)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, NOT_EXPLAINABLE));
        if (!explainable(day)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, NOT_EXPLAINABLE);
        }
        if (settled(cid, YearMonth.from(workDate))) {
            throw new BusinessException(ErrorCode.PAY_MONTH_SETTLED);
        }
        validateTimes(request, day, workDate);
        if (hasPending(cid, employeeId, workDate)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "그날 대기 중인 소명이 있습니다");
        }
        AttendanceExplanation explanation = new AttendanceExplanation(em.getReference(Employee.class, employeeId),
                workDate, request.reason().trim(), request.workType(), request.checkInAt(), request.checkOutAt());
        try {
            repository.saveAndFlush(explanation);
        } catch (DataIntegrityViolationException e) {
            // 사전 검사와 저장 사이에 같은 날 소명이 먼저 들어온 경우
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(PENDING_UNIQUE)) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "그날 대기 중인 소명이 있습니다");
            }
            throw e;
        }
        return response(explanation, null);
    }

    /** 내 소명 — 그 달 것만, 최근 날짜부터. */
    @Transactional(readOnly = true)
    public List<AttendanceExplanationResponse> mine(LoginUser user, YearMonth month) {
        YearMonth m = month != null ? month : YearMonth.now(clock);
        return jdbc.query("""
                        SELECT id, employee_id, work_date, reason, req_check_in_at, req_check_out_at,
                               req_work_type::text AS req_work_type, status::text AS status, reject_reason, reviewed_at,
                               created_at
                        FROM attendance_explanation
                        WHERE company_id = ? AND employee_id = ? AND work_date BETWEEN ? AND ?
                        ORDER BY work_date DESC, id DESC
                        """,
                (rs, i) -> {
                    String workType = rs.getString("req_work_type");
                    return new AttendanceExplanationResponse(rs.getLong("id"), rs.getLong("employee_id"),
                            rs.getObject("work_date", LocalDate.class), rs.getString("reason"),
                            seoul(rs.getObject("req_check_in_at", OffsetDateTime.class)),
                            seoul(rs.getObject("req_check_out_at", OffsetDateTime.class)),
                            workType == null ? null : WorkType.valueOf(workType),
                            RequestStatus.valueOf(rs.getString("status")), rs.getString("reject_reason"),
                            seoul(rs.getObject("reviewed_at", OffsetDateTime.class)),
                            seoul(rs.getObject("created_at", OffsetDateTime.class)), null);
                },
                user.companyId(), user.employeeId(), Date.valueOf(m.atDay(1)), Date.valueOf(m.atEndOfMonth()));
    }

    /** 대기 중인 본인 소명만 철회한다. 남의 소명은 404. */
    @Transactional
    public AttendanceExplanationResponse withdraw(LoginUser user, long id) {
        AttendanceExplanation explanation = repository.findById(id)
                .filter(e -> e.getEmployee().getId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        lockPending(explanation);
        explanation.cancel();
        repository.flush();
        return response(explanation, null);
    }

    /**
     * 승인 — 요청 값이 있으면 그 값으로 정정, 없으면 근태가 있을 때 "확인"(정정 사유만)을 남긴다. 정정 사유는 "소명: {사유}".
     * 근태 행이 없는 날(결근)은 출퇴근 시각으로 근태를 새로 만든다(신청할 때 둘 다 받았다).
     */
    @Transactional
    public AttendanceExplanationResponse approve(LoginUser user, long id) {
        AttendanceExplanation explanation = find(id);
        lockPending(explanation);
        long employeeId = explanation.getEmployee().getId();
        String reason = "소명: " + explanation.getReason();
        Long attendanceId = jdbc.queryForList("SELECT id FROM attendance WHERE company_id = ? AND employee_id = ? AND work_date = ?",
                Long.class, user.companyId(), employeeId, Date.valueOf(explanation.getWorkDate())).stream().findFirst()
                .orElse(null);
        AttendanceCorrectionResponse corrected = null;
        if (attendanceId != null) {
            Map<String, Object> patch = new LinkedHashMap<>();
            if (explanation.getReqCheckOutAt() != null) {
                patch.put("status", AttendanceStatus.CHECKED_OUT.name());
            }
            if (explanation.getReqCheckInAt() != null) {
                patch.put("checkInAt", explanation.getReqCheckInAt().toString());
            }
            if (explanation.getReqCheckOutAt() != null) {
                patch.put("checkOutAt", explanation.getReqCheckOutAt().toString());
            }
            if (explanation.getReqWorkType() != null) {
                patch.put("workType", explanation.getReqWorkType().name());
            }
            patch.put("reason", reason);
            corrected = correctionService.correct(user, attendanceId, patch);
        } else if (explanation.hasRequestedValues()) {
            corrected = correctionService.create(user, new AttendanceCreateRequest(employeeId, explanation.getWorkDate(),
                    AttendanceStatus.CHECKED_OUT, explanation.getReqCheckInAt(), explanation.getReqCheckOutAt(),
                    explanation.getReqWorkType() != null ? explanation.getReqWorkType() : WorkType.OFFICE, reason));
        }
        explanation.approve(em.getReference(Employee.class, user.employeeId()), now());
        repository.flush();
        return response(explanation, corrected == null ? null : corrected.warning());
    }

    /** 반려 — 사유 필수. 근태는 건드리지 않는다. */
    @Transactional
    public AttendanceExplanationResponse reject(LoginUser user, long id, String rejectReason) {
        AttendanceExplanation explanation = find(id);
        lockPending(explanation);
        explanation.reject(em.getReference(Employee.class, user.employeeId()), now(), rejectReason.trim());
        repository.flush();
        return response(explanation, null);
    }

    // ------------------------------------------------------------------

    private static boolean explainable(AttendanceDay day) {
        return Boolean.TRUE.equals(day.late()) || Boolean.TRUE.equals(day.earlyLeave())
                || (day.status() != null && EXPLAINABLE.contains(day.status()));
    }

    /**
     * 퇴근은 출근보다 늦어야 한다. 결근인 날(근태 행이 없다)은 시각을 내려면 둘 다, 퇴근이 없는 날은 퇴근을 넣어야 정정할 수 있다.
     * 출근은 그날, 퇴근은 다음 날까지 — 승인 때 정정 규칙(AttendanceCorrectionService)에 막힐 값은 신청할 때 거부한다.
     * 근무 형태는 사내 · 재택 · 외근만.
     */
    private static void validateTimes(AttendanceExplanationRequest request, AttendanceDay day, LocalDate workDate) {
        Map<String, String> errors = new LinkedHashMap<>();
        OffsetDateTime in = request.checkInAt();
        OffsetDateTime out = request.checkOutAt();
        boolean anyValue = in != null || out != null || request.workType() != null;
        if (in != null && !seoul(in).toLocalDate().equals(workDate)) {
            errors.put("checkInAt", "출근 시각은 그날 날짜로 입력하세요");
        }
        if (out != null && (seoul(out).toLocalDate().isBefore(workDate)
                || seoul(out).toLocalDate().isAfter(workDate.plusDays(1)))) {
            errors.put("checkOutAt", "퇴근 시각은 그날부터 다음 날까지만 입력할 수 있습니다");
        }
        if (in != null && out != null && !out.isAfter(in)) {
            errors.putIfAbsent("checkOutAt", "퇴근은 출근보다 늦어야 합니다");
        }
        if (day.status() == AttendanceDayStatus.ABSENT && anyValue) {
            if (in == null) {
                errors.put("checkInAt", "결근인 날은 출근과 퇴근을 둘 다 입력하세요");
            }
            if (out == null) {
                errors.put("checkOutAt", "결근인 날은 출근과 퇴근을 둘 다 입력하세요");
            }
        }
        boolean noCheckOut = day.status() == AttendanceDayStatus.MISSING_CHECKOUT
                || day.status() == AttendanceDayStatus.CHECKED_IN;
        if (noCheckOut && out == null && (anyValue || day.status() == AttendanceDayStatus.CHECKED_IN)) {
            errors.putIfAbsent("checkOutAt", "퇴근 시각을 입력하세요");
        }
        if (request.workType() != null && !SELECTABLE_WORK_TYPES.contains(request.workType())) {
            errors.put("workType", "근무 형태는 사내 · 재택 · 외근 중에서 고르세요");
        }
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
    }

    private AttendanceExplanation find(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 처리 순서가 겹치면 먼저 커밋한 쪽을 기다린 뒤 최신 상태로 다시 판단한다. */
    private void lockPending(AttendanceExplanation explanation) {
        em.refresh(explanation, LockModeType.PESSIMISTIC_WRITE);
        if (explanation.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "대기 중인 소명만 처리할 수 있습니다");
        }
    }

    private boolean hasPending(long companyId, long employeeId, LocalDate workDate) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM attendance_explanation
                                       WHERE company_id = ? AND employee_id = ? AND work_date = ? AND status = 'PENDING')
                        """, Boolean.class, companyId, employeeId, Date.valueOf(workDate)));
    }

    private boolean settled(long companyId, YearMonth month) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payroll_run WHERE company_id = ? AND pay_month = ?)",
                Boolean.class, companyId, month.toString()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static AttendanceExplanationResponse response(AttendanceExplanation e, String warning) {
        return new AttendanceExplanationResponse(e.getId(), e.getEmployee().getId(), e.getWorkDate(), e.getReason(),
                seoul(e.getReqCheckInAt()), seoul(e.getReqCheckOutAt()), e.getReqWorkType(), e.getStatus(),
                e.getRejectReason(), seoul(e.getReviewedAt()), seoul(e.getCreatedAt()), warning);
    }
}
