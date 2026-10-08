package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceCorrectionResponse;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.CorrectionItem;
import com.nexuslabs.hr.domain.attendance.entity.Attendance;
import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.BusinessTrip;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.attendance.repository.AttendanceRepository;
import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.leave.entity.LeaveRequest;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 근태 정정(F-ATT-04) — 정정 대상 목록 · 정정 · 기록 없는 날의 근태 생성. ATTENDANCE_MANAGE 만 쓴다.
 * 근태는 수당 금액에 영향을 주므로 정정은 감사 로그로 남긴다(BR-AUDIT-001).
 * 정정은 JPA, 목록은 여러 영역을 묶는 조회라 JDBC 다. JDBC 쿼리에는 company_id 조건을 직접 넣는다.
 */
@Service
public class AttendanceCorrectionService {

    private static final Set<String> PATCH_FIELDS = Set.of("status", "checkInAt", "checkOutAt", "workType", "reason");
    /** 출퇴근 기록의 근무 형태. 출장(BUSINESS_TRIP)은 출장 근태에만 쓴다. */
    private static final Set<WorkType> SELECTABLE_WORK_TYPES = Set.of(WorkType.OFFICE, WorkType.REMOTE, WorkType.FIELD);
    private static final int REASON_MAX_LENGTH = 255;
    private static final String SETTLED_WARNING = "PAY_MONTH_SETTLED";
    /** 직원 · 날짜 유일 제약(schema.sql). */
    private static final String ATTENDANCE_UNIQUE = "attendance_employee_id_work_date_key";

    private final AttendanceRepository attendanceRepository;
    private final TodayStatusReader todayStatusReader;
    private final WorkCalendar workCalendar;
    private final AuditLogger auditLogger;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public AttendanceCorrectionService(AttendanceRepository attendanceRepository, TodayStatusReader todayStatusReader,
                                       WorkCalendar workCalendar, AuditLogger auditLogger, JdbcTemplate jdbc,
                                       EntityManager em, Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.todayStatusReader = todayStatusReader;
        this.workCalendar = workCalendar;
        this.auditLogger = auditLogger;
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    /** 정정 대상 목록. 오래된 날짜부터. */
    @Transactional(readOnly = true)
    public List<CorrectionItem> list(LoginUser user) {
        return corrections(user.companyId());
    }

    /** 정정 대상 건수 — /me 의 할 일 배지가 쓴다(역할 분담 v2 2.1). 목록과 같은 조건이라 건수가 항상 같다. */
    @Transactional(readOnly = true)
    public long countCorrections(long companyId) {
        return corrections(companyId).size();
    }

    /**
     * 정정. 보낸 필드만 바꾼다(API 설계서 1.1) — reason 은 항상 필요하다.
     * 값 필드(status · checkInAt · checkOutAt · workType)를 하나도 보내지 않으면 기록은 그대로 두고 사유만 남기는 "확인"이다.
     */
    @Transactional
    public AttendanceCorrectionResponse correct(LoginUser user, long attendanceId, Map<String, Object> patch) {
        PatchRequest.check(patch, PATCH_FIELDS, Set.of("status", "workType", "reason"));
        Map<String, String> errors = new LinkedHashMap<>();
        String reason = parseReason(patch.get("reason"), errors);
        boolean statusChosen = patch.containsKey("status");
        AttendanceStatus chosen = statusChosen ? parseEnum(AttendanceStatus.class, patch.get("status")) : null;
        WorkType chosenWorkType = patch.containsKey("workType") ? parseEnum(WorkType.class, patch.get("workType")) : null;
        OffsetDateTime sentCheckIn = parseTime("checkInAt", patch.get("checkInAt"), errors);
        OffsetDateTime sentCheckOut = parseTime("checkOutAt", patch.get("checkOutAt"), errors);
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Map<String, Object> before = snapshot(attendance);
        boolean valuesSent = PATCH_FIELDS.stream().anyMatch(f -> !f.equals("reason") && patch.containsKey(f));
        if (!valuesSent) {
            if (attendance.getStatus() == AttendanceStatus.CHECKED_IN
                    && !attendance.getWorkDate().equals(LocalDate.now(clock))) {
                throw BusinessException.invalidFields(Map.of("status", "지난 날짜의 출근 기록은 상태를 퇴근으로 정정하세요"));
            }
            attendance.confirm(reason, user.employeeId(), now());
        } else {
            Values values = resolve(user.companyId(), attendance.getEmployee().getId(), attendance.getWorkDate(),
                    statusChosen ? chosen : attendance.getStatus(), statusChosen,
                    chosenWorkType != null ? chosenWorkType : attendance.getWorkType(), chosenWorkType != null,
                    patch.containsKey("checkInAt") ? sentCheckIn : attendance.getCheckInAt(),
                    patch.containsKey("checkOutAt") ? sentCheckOut : attendance.getCheckOutAt(),
                    sentCheckIn != null, sentCheckOut != null);
            apply(attendance, values, reason, user);
        }
        auditLogger.log(user, AuditAction.UPDATE, "ATTENDANCE", attendanceId, before, snapshot(attendance));
        return response(user.companyId(), attendance);
    }

    /** 기록이 없는 날(결근 처리된 날 등)에 근태를 만든다. 그날 근태가 이미 있으면 정정(PATCH)으로 고친다. */
    @Transactional
    public AttendanceCorrectionResponse create(LoginUser user, AttendanceCreateRequest request) {
        LocalDate hireDate = jdbc.query("SELECT hire_date FROM employee WHERE id = ? AND company_id = ?",
                        (rs, i) -> rs.getObject("hire_date", LocalDate.class), request.employeeId(), user.companyId())
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (request.workDate().isAfter(LocalDate.now(clock))) {
            throw BusinessException.invalidFields(Map.of("workDate", "미래 날짜는 입력할 수 없습니다"));
        }
        if (request.workDate().isBefore(hireDate)) {
            throw BusinessException.invalidFields(Map.of("workDate", "입사일 이전 날짜는 입력할 수 없습니다"));
        }
        if (attendanceRepository.findByEmployeeIdAndWorkDate(request.employeeId(), request.workDate()).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "그날 근태가 이미 있습니다. 그 기록을 정정하세요");
        }
        Values values = resolve(user.companyId(), request.employeeId(), request.workDate(), request.status(), true,
                request.workType(), request.workType() != null, truncate(request.checkInAt()),
                truncate(request.checkOutAt()), request.checkInAt() != null, request.checkOutAt() != null);
        Attendance attendance = Attendance.forCorrection(em.getReference(Employee.class, request.employeeId()),
                request.workDate(), values.status());
        apply(attendance, values, request.reason().trim(), user);
        try {
            attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException e) {
            // 사전 검사와 저장 사이에 그날 근태가 생긴 경우(동시 요청 · 본인 출근) — 출근 API 의 에러 코드가 아니라 문서대로
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(ATTENDANCE_UNIQUE)) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "그날 근태가 이미 있습니다. 그 기록을 정정하세요");
            }
            throw e;
        }
        auditLogger.log(user, AuditAction.CREATE, "ATTENDANCE", attendance.getId(), null, snapshot(attendance));
        return response(user.companyId(), attendance);
    }

    // ------------------------------------------------------------------ 정정 대상

    /**
     * 정정 대상(F-ATT-04) —
     * ① 퇴근미기록, 그리고 지난 날짜인데 아직 출근 상태인 기록(자정을 넘겨 이어지는 어제 근무는 야간 끝까지 빼고 본다)
     * ② 승인된 휴가 · 출장 기간의 근무일인데 출근 기록이 있는 충돌. 주말 · 휴일 출근은 충돌이 아니다(휴가 근태는 근무일에만 생긴다).
     *    충돌 건은 휴가 · 출장이 승인된 뒤에 정정(확인)했으면 뺀다.
     * 한 기록이 둘 다에 해당하면 퇴근미기록 한 건으로만 센다.
     */
    private List<CorrectionItem> corrections(long companyId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate openBefore = todayStatusReader.yesterdayStillOpen(companyId) ? today.minusDays(1) : today;
        WorkCalendar.Snapshot calendar = workCalendar.snapshot(companyId);
        List<CorrectionItem> items = new ArrayList<>();
        jdbc.query("""
                        SELECT * FROM (
                        SELECT a.id, a.employee_id, e.employee_no, e.name, o.name AS org_unit_name, a.work_date,
                               a.status::text AS status, a.work_type::text AS work_type, a.check_in_at, a.check_out_at,
                               a.corrected_at,
                               l.id AS leave_id, lt.name AS leave_name, l.start_date AS leave_start, l.end_date AS leave_end,
                               COALESCE((SELECT max(s.acted_at) FROM approval_step s
                                         WHERE s.company_id = a.company_id AND s.work_type = 'LEAVE'
                                           AND s.target_id = l.id AND s.status = 'APPROVED'), l.created_at) AS leave_approved_at,
                               b.id AS trip_id, b.destination, b.start_date AS trip_start, b.end_date AS trip_end,
                               COALESCE((SELECT max(s.acted_at) FROM approval_step s
                                         WHERE s.company_id = a.company_id AND s.work_type = 'BUSINESS_TRIP'
                                           AND s.target_id = b.id AND s.status = 'APPROVED'), b.created_at) AS trip_approved_at
                        FROM attendance a
                             JOIN employee e ON e.id = a.employee_id AND e.company_id = a.company_id
                             JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                             LEFT JOIN leave_request l ON l.company_id = a.company_id AND l.employee_id = a.employee_id
                                  AND l.status IN ('APPROVED', 'CANCEL_REQUESTED')
                                  AND a.work_date BETWEEN l.start_date AND l.end_date
                             LEFT JOIN leave_type lt ON lt.id = l.leave_type_id AND lt.company_id = l.company_id
                             LEFT JOIN business_trip b ON b.company_id = a.company_id AND b.employee_id = a.employee_id
                                  AND b.status = 'APPROVED' AND a.work_date BETWEEN b.start_date AND b.end_date
                        WHERE a.company_id = ? AND a.check_in_at IS NOT NULL
                          AND (a.status = 'MISSING_CHECKOUT' OR (a.status = 'CHECKED_IN' AND a.work_date < ?)
                               OR l.id IS NOT NULL OR b.id IS NOT NULL)
                        ) x
                        -- 승인 뒤에 정정(확인)한 충돌은 여기서 뺀다 — 확인이 쌓여도 읽는 행이 늘지 않는다
                        WHERE x.status = 'MISSING_CHECKOUT' OR (x.status = 'CHECKED_IN' AND x.work_date < ?)
                           OR (x.leave_id IS NOT NULL
                               AND (x.corrected_at IS NULL OR x.corrected_at <= x.leave_approved_at))
                           OR (x.trip_id IS NOT NULL
                               AND (x.corrected_at IS NULL OR x.corrected_at <= x.trip_approved_at))
                        ORDER BY x.work_date, x.id
                        """,
                rs -> {
                    LocalDate workDate = rs.getObject("work_date", LocalDate.class);
                    AttendanceStatus status = AttendanceStatus.valueOf(rs.getString("status"));
                    OffsetDateTime correctedAt = rs.getObject("corrected_at", OffsetDateTime.class);
                    CorrectionItem.Type type = null;
                    CorrectionItem.Conflict conflict = null;
                    if (status == AttendanceStatus.MISSING_CHECKOUT
                            || (status == AttendanceStatus.CHECKED_IN && workDate.isBefore(openBefore))) {
                        type = CorrectionItem.Type.MISSING_CHECKOUT;
                    } else if (calendar.isWorkday(workDate)) {
                        if (rs.getObject("leave_id") != null
                                && !confirmedAfter(correctedAt, rs.getObject("leave_approved_at", OffsetDateTime.class))) {
                            type = CorrectionItem.Type.LEAVE_CONFLICT;
                            conflict = new CorrectionItem.Conflict(rs.getLong("leave_id"), rs.getString("leave_name"),
                                    rs.getObject("leave_start", LocalDate.class), rs.getObject("leave_end", LocalDate.class));
                        } else if (rs.getObject("trip_id") != null
                                && !confirmedAfter(correctedAt, rs.getObject("trip_approved_at", OffsetDateTime.class))) {
                            type = CorrectionItem.Type.TRIP_CONFLICT;
                            conflict = new CorrectionItem.Conflict(rs.getLong("trip_id"), rs.getString("destination"),
                                    rs.getObject("trip_start", LocalDate.class), rs.getObject("trip_end", LocalDate.class));
                        }
                    }
                    if (type != null) {
                        String workType = rs.getString("work_type");
                        items.add(new CorrectionItem(type, rs.getLong("id"), rs.getLong("employee_id"),
                                rs.getString("employee_no"), rs.getString("name"), rs.getString("org_unit_name"),
                                workDate, status, workType == null ? null : WorkType.valueOf(workType),
                                seoul(rs.getObject("check_in_at", OffsetDateTime.class)),
                                seoul(rs.getObject("check_out_at", OffsetDateTime.class)), conflict));
                    }
                },
                companyId, Date.valueOf(openBefore), Date.valueOf(openBefore));
        return items;
    }

    /** 휴가 · 출장이 승인된 뒤에 정정했으면 관리자가 확인한 것이다. */
    private static boolean confirmedAfter(OffsetDateTime correctedAt, OffsetDateTime approvedAt) {
        return correctedAt != null && approvedAt != null && correctedAt.isAfter(approvedAt);
    }

    // ------------------------------------------------------------------ 정정 규칙

    /** 정정으로 저장할 값. 휴가 · 출장이면 연결할 ID 가 있고 출퇴근 시각은 없다. */
    private record Values(AttendanceStatus status, WorkType workType, OffsetDateTime checkInAt,
                          OffsetDateTime checkOutAt, Long leaveRequestId, Long businessTripId) {
    }

    /**
     * 바꿀 수 있는 상태(F-ATT-04) — 퇴근(CHECKED_OUT, 출퇴근 시각 필수) · 휴가/출장(그 날짜를 덮는 승인된 휴가 · 출장이 있을 때만) ·
     * 출근(CHECKED_IN, 오늘 기록만). 퇴근미기록은 시스템 상태라 고를 수 없다(이미 퇴근미기록인 기록을 그대로 두는 것은 된다).
     *
     * @param statusChosen status 를 요청으로 보냈는지 — 보내지 않았으면 지금 상태를 그대로 둔다
     */
    private Values resolve(long companyId, long employeeId, LocalDate workDate, AttendanceStatus status,
                           boolean statusChosen, WorkType workType, boolean workTypeChosen, OffsetDateTime checkInAt,
                           OffsetDateTime checkOutAt, boolean checkInSent, boolean checkOutSent) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (status == AttendanceStatus.ON_VACATION || status == AttendanceStatus.ON_BUSINESS_TRIP) {
            if (checkInSent || checkOutSent || workTypeChosen) {
                errors.put(checkInSent ? "checkInAt" : checkOutSent ? "checkOutAt" : "workType",
                        "휴가 · 출장으로 정정할 때는 출퇴근 시각과 근무 형태를 보내지 않습니다");
                throw BusinessException.invalidFields(errors);
            }
            boolean vacation = status == AttendanceStatus.ON_VACATION;
            Long coverId = vacation ? approvedLeaveOn(companyId, employeeId, workDate)
                    : approvedTripOn(companyId, employeeId, workDate);
            if (coverId == null) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        vacation ? "그 날짜에 승인된 휴가가 없습니다" : "그 날짜에 승인된 출장이 없습니다");
            }
            return vacation ? new Values(status, null, null, null, coverId, null)
                    : new Values(status, WorkType.BUSINESS_TRIP, null, null, null, coverId);
        }
        if (statusChosen && status == AttendanceStatus.MISSING_CHECKOUT) {
            errors.put("status", "퇴근미기록으로는 정정할 수 없습니다");
        }
        if (status == AttendanceStatus.CHECKED_IN && !workDate.equals(LocalDate.now(clock))) {
            // 보낸 값이든 그대로 둔 값이든 — 지난 날짜가 출근 상태로 남으면 월 조회와 정정 목록의 표시가 갈린다
            errors.put("status", statusChosen ? "출근 상태로는 오늘 기록만 정정할 수 있습니다"
                    : "지난 날짜의 출근 기록은 상태를 퇴근으로 정정하세요");
        }
        if (checkInAt == null) {
            errors.put("checkInAt", "출근 시각을 입력하세요");
        } else if (!localDate(checkInAt).equals(workDate)) {
            errors.put("checkInAt", "근무일의 시각이어야 합니다");
        }
        if (status == AttendanceStatus.CHECKED_OUT) {
            if (checkOutAt == null) {
                errors.put("checkOutAt", "퇴근 시각을 입력하세요");
            } else if (checkInAt != null && !checkOutAt.isAfter(checkInAt)) {
                errors.put("checkOutAt", "퇴근 시각은 출근 시각보다 늦어야 합니다");
            } else if (localDate(checkOutAt).isAfter(workDate.plusDays(1))) {
                errors.put("checkOutAt", "퇴근 시각은 근무일 다음 날까지만 입력할 수 있습니다");
            }
        } else if (checkOutSent) {
            errors.put("checkOutAt", "퇴근 시각을 넣으려면 상태를 퇴근으로 바꾸세요");
        } else {
            checkOutAt = null;
        }
        if (workType == null || workType == WorkType.BUSINESS_TRIP) {
            if (workTypeChosen) {
                errors.put("workType", "사내 · 재택 · 외근 중에서 고르세요");
            }
            workType = WorkType.OFFICE;     // 휴가 · 출장 기록을 출퇴근 기록으로 바꾸는데 근무 형태를 보내지 않은 경우
        } else if (!SELECTABLE_WORK_TYPES.contains(workType)) {
            errors.put("workType", "사내 · 재택 · 외근 중에서 고르세요");
        }
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        return new Values(status, workType, checkInAt, checkOutAt, null, null);
    }

    private void apply(Attendance attendance, Values values, String reason, LoginUser user) {
        attendance.correct(values.status(), values.workType(), values.checkInAt(), values.checkOutAt(),
                values.leaveRequestId() == null ? null : em.getReference(LeaveRequest.class, values.leaveRequestId()),
                values.businessTripId() == null ? null : em.getReference(BusinessTrip.class, values.businessTripId()),
                reason, user.employeeId(), now());
    }

    private Long approvedLeaveOn(long companyId, long employeeId, LocalDate date) {
        return jdbc.query("""
                        SELECT id FROM leave_request
                        WHERE company_id = ? AND employee_id = ? AND status IN ('APPROVED', 'CANCEL_REQUESTED')
                          AND ? BETWEEN start_date AND end_date
                        """,
                (rs, i) -> rs.getLong("id"), companyId, employeeId, Date.valueOf(date)).stream().findFirst().orElse(null);
    }

    private Long approvedTripOn(long companyId, long employeeId, LocalDate date) {
        return jdbc.query("""
                        SELECT id FROM business_trip
                        WHERE company_id = ? AND employee_id = ? AND status = 'APPROVED'
                          AND ? BETWEEN start_date AND end_date
                        """,
                (rs, i) -> rs.getLong("id"), companyId, employeeId, Date.valueOf(date)).stream().findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ 응답 · 입력

    private AttendanceCorrectionResponse response(long companyId, Attendance a) {
        Boolean settled = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payroll_run WHERE company_id = ? AND pay_month = ?)",
                Boolean.class, companyId, YearMonth.from(a.getWorkDate()).toString());
        return new AttendanceCorrectionResponse(a.getId(), a.getEmployee().getId(), a.getWorkDate(), a.getStatus(),
                a.getWorkType(), seoul(a.getCheckInAt()), seoul(a.getCheckOutAt()), a.getCorrectionReason(),
                seoul(a.getCorrectedAt()), Boolean.TRUE.equals(settled) ? SETTLED_WARNING : null);
    }

    /** 감사 로그에 남길 값. */
    private static Map<String, Object> snapshot(Attendance a) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", a.getStatus().name());
        values.put("workType", a.getWorkType() == null ? null : a.getWorkType().name());
        values.put("checkInAt", a.getCheckInAt() == null ? null : seoul(a.getCheckInAt()).toString());
        values.put("checkOutAt", a.getCheckOutAt() == null ? null : seoul(a.getCheckOutAt()).toString());
        values.put("leaveRequestId", a.getLeaveRequest() == null ? null : a.getLeaveRequest().getId());
        values.put("businessTripId", a.getBusinessTrip() == null ? null : a.getBusinessTrip().getId());
        values.put("reason", a.getCorrectionReason());
        return values;
    }

    private static String parseReason(Object value, Map<String, String> errors) {
        if (!(value instanceof String text) || text.isBlank()) {
            errors.put("reason", "정정 사유를 입력하세요");
            return null;
        }
        if (text.trim().length() > REASON_MAX_LENGTH) {
            errors.put("reason", REASON_MAX_LENGTH + "자 이하로 입력하세요");
            return null;
        }
        return text.trim();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, Object value) {
        return Arrays.stream(type.getEnumConstants()).filter(c -> c.name().equals(value)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ENUM_VALUE,
                        Map.of("allowed", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList())));
    }

    /** null 이면 null(비움). 형식이 틀리면 errors 에 담는다. */
    private static OffsetDateTime parseTime(String field, Object value, Map<String, String> errors) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(OffsetDateTime.parse(String.valueOf(value)));
        } catch (DateTimeParseException e) {
            errors.put(field, "시각 형식(2026-10-01T09:00:00+09:00)으로 입력하세요");
            return null;
        }
    }

    private OffsetDateTime now() {
        return truncate(OffsetDateTime.now(clock));
    }

    private static OffsetDateTime truncate(OffsetDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.SECONDS);
    }

    private static LocalDate localDate(OffsetDateTime time) {
        return time.atZoneSameInstant(ClockConfig.ZONE).toLocalDate();
    }

    private static OffsetDateTime seoul(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }
}
