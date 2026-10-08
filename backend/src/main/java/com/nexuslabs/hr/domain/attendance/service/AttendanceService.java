package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.dto.CheckInRequest;
import com.nexuslabs.hr.domain.attendance.dto.CheckOutRequest;
import com.nexuslabs.hr.domain.attendance.dto.TodayAttendance;
import com.nexuslabs.hr.domain.attendance.entity.Attendance;
import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.RecordMethod;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.attendance.repository.AttendanceRepository;
import com.nexuslabs.hr.domain.company.service.WorkCalendar;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 근태(F-ATT-01–05) — 출근 · 퇴근 · 근무 형태 변경 · 오늘 조회와 휴가 연동. 근태 계산 · 월 조회 · 정정은 B-11 의 다음 PR 에서 더한다.
 * 출퇴근은 JPA, 휴가 연동은 휴가 영역(JDBC)이 승인 트랜잭션 안에서 부르므로 JDBC 다. JDBC 쿼리에는 company_id 조건을 직접 넣는다.
 */
@Service
public class AttendanceService {

    /** 본인이 고를 수 있는 근무 형태. 출장(BUSINESS_TRIP)은 출장이 승인될 때 시스템이 넣는다. */
    private static final Set<WorkType> SELECTABLE_WORK_TYPES = Set.of(WorkType.OFFICE, WorkType.REMOTE, WorkType.FIELD);
    private static final Set<String> PATCH_FIELDS = Set.of("workType", "placeMemo");
    private static final int PLACE_MEMO_MAX_LENGTH = 100;
    private static final Pattern MOBILE_USER_AGENT = Pattern.compile("Mobi|Android|iPhone|iPad");

    private final AttendanceRepository attendanceRepository;
    private final TodayStatusReader todayStatusReader;
    private final WorkCalendar workCalendar;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendanceRepository, TodayStatusReader todayStatusReader,
                             WorkCalendar workCalendar, JdbcTemplate jdbc, EntityManager em, Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.todayStatusReader = todayStatusReader;
        this.workCalendar = workCalendar;
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    /**
     * 출근(F-ATT-01). 서버 시각으로 기록한다. 근무일이 아닌 날에도 출근할 수 있다(휴일 근무).
     * 오늘 이전에 퇴근하지 않은 출근 상태 기록은 모두 퇴근미기록으로 바꾼다(BR-ATT-002) — 정정 대상 목록에 나온다.
     */
    @Transactional
    public TodayAttendance checkIn(LoginUser user, CheckInRequest request, String userAgent) {
        WorkType workType = request.workType() != null ? request.workType() : WorkType.OFFICE;
        Map<String, String> errors = new LinkedHashMap<>();
        if (!SELECTABLE_WORK_TYPES.contains(workType)) {
            errors.put("workType", "사내 · 재택 · 외근 중에서 고르세요");
        }
        checkLocation(request.lat(), request.lng(), errors);
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        EmpStatus empStatus = requireActive(user);
        LocalDate today = LocalDate.now(clock);
        attendanceRepository.findByEmployeeIdAndWorkDate(user.employeeId(), today).ifPresent(existing -> {
            throw new BusinessException(isLeaveOrTrip(existing) ? ErrorCode.ATT_ON_LEAVE_OR_TRIP
                    : ErrorCode.ATT_ALREADY_CHECKED_IN);
        });
        // JPA 로 오늘 행을 넣기 전에 JDBC 로 먼저 바꾼다(백엔드 안내 6.1) — 지난 행은 이 트랜잭션에서 읽은 적이 없다
        jdbc.update("""
                        UPDATE attendance SET status = 'MISSING_CHECKOUT', updated_at = now()
                        WHERE company_id = ? AND employee_id = ? AND work_date < ? AND status = 'CHECKED_IN'
                        """,
                user.companyId(), user.employeeId(), Date.valueOf(today));
        // 동시에 두 번 누르면 유일 제약(employee_id, work_date)이 막고 ATT_ALREADY_CHECKED_IN 으로 응답한다
        Attendance attendance = attendanceRepository.saveAndFlush(Attendance.checkIn(
                em.getReference(Employee.class, user.employeeId()), today, workType, blankToNull(request.placeMemo()),
                now(), recordMethod(userAgent), scaled(request.lat()), scaled(request.lng())));
        return view(user, empStatus, attendance, today);
    }

    /**
     * 퇴근(F-ATT-02). 자정을 넘겼으면(오늘 기록이 없고 어제 기록이 출근 상태) 어제 야간 끝 전까지는 어제 기록을 닫는다.
     */
    @Transactional
    public TodayAttendance checkOut(LoginUser user, CheckOutRequest request, String userAgent) {
        Map<String, String> errors = new LinkedHashMap<>();
        checkLocation(request.lat(), request.lng(), errors);
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        EmpStatus empStatus = requireActive(user);
        LocalDate today = LocalDate.now(clock);
        Attendance attendance = requireCheckedIn(current(user, today));
        // DB 는 퇴근 시각이 출근 시각보다 늦어야 한다 — 출근 직후 같은 초에 퇴근해도 1초 뒤로 기록한다
        OffsetDateTime at = now();
        OffsetDateTime earliest = attendance.getCheckInAt().plusSeconds(1);
        attendance.checkOut(at.isBefore(earliest) ? earliest : at, recordMethod(userAgent), scaled(request.lat()),
                scaled(request.lng()));
        return view(user, empStatus, attendance, today);
    }

    /**
     * 근무 형태 변경(F-ATT-01) — 퇴근 전까지. 보낸 필드만 바꾸고 placeMemo 는 null 로 비울 수 있다(API 설계서 1.1).
     * 자정을 넘겨 어제 기록을 보여 주는 동안에는 어제 기록에 적용한다.
     */
    @Transactional
    public TodayAttendance changeWorkType(LoginUser user, Map<String, Object> patch) {
        PatchRequest.check(patch, PATCH_FIELDS, Set.of("workType"));
        EmpStatus empStatus = requireActive(user);
        LocalDate today = LocalDate.now(clock);
        Attendance attendance = requireCheckedIn(current(user, today));
        WorkType workType = patch.containsKey("workType") ? parseWorkType(patch.get("workType"))
                : attendance.getWorkType();
        String placeMemo = patch.containsKey("placeMemo") ? parsePlaceMemo(patch.get("placeMemo"))
                : attendance.getPlaceMemo();
        attendance.changeWorkType(workType, placeMemo);
        return view(user, empStatus, attendance, today);
    }

    /** 오늘 근태(홈 출퇴근 버튼용). 대시보드의 me.today 도 이 값을 쓴다(역할 분담 v2 2.1). */
    @Transactional(readOnly = true)
    public TodayAttendance today(LoginUser user) {
        LocalDate today = LocalDate.now(clock);
        return view(user, empStatus(user), current(user, today).orElse(null), today);
    }

    /**
     * 휴가가 최종 승인될 때 같은 트랜잭션에서 부른다(역할 분담 v2 2.1, BR-APPR-005).
     * 휴가 기간의 근무일마다 휴가 근태(ON_VACATION)를 만든다. 근무일은 승인하는 지금의 근무시간·휴일로 판정한다.
     * 이미 근태가 있는 날(출근 기록)은 덮어쓰지 않고 건너뛴다 — 그날은 정정 대상 목록에 충돌로 나온다(BR-ATT-001).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createLeaveDays(long companyId, long leaveRequestId) {
        LeavePeriod leave = jdbc.query("""
                        SELECT employee_id, start_date, end_date FROM leave_request WHERE id = ? AND company_id = ?
                        """,
                (rs, i) -> new LeavePeriod(rs.getLong("employee_id"), rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class)),
                leaveRequestId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        WorkCalendar.Snapshot calendar = workCalendar.snapshot(companyId);
        List<Object[]> rows = new ArrayList<>();
        for (LocalDate date = leave.startDate(); !date.isAfter(leave.endDate()); date = date.plusDays(1)) {
            if (calendar.isWorkday(date)) {
                rows.add(new Object[]{companyId, leave.employeeId(), Date.valueOf(date), leaveRequestId});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, check_in_method, leave_request_id)
                VALUES (?, ?, ?, 'ON_VACATION', 'SYSTEM', ?)
                ON CONFLICT (employee_id, work_date) DO NOTHING
                """, rows);
    }

    /**
     * 출장이 최종 승인될 때 같은 트랜잭션에서 부른다(역할 분담 v2 2.1, BR-ATT-005). createLeaveDays 와 같은 방식 —
     * 출장 기간의 근무일마다 출장 근태(ON_BUSINESS_TRIP)를 만들고, 이미 근태가 있는 날은 건너뛴다(정정 대상의 충돌).
     * 근무일은 승인하는 지금의 근무시간·휴일로 판정한다. 출장일 근무시간은 1일 소정근로시간으로 본다(근태 계산).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createTripDays(long companyId, long businessTripId) {
        LeavePeriod trip = jdbc.query("""
                        SELECT employee_id, start_date, end_date FROM business_trip WHERE id = ? AND company_id = ?
                        """,
                (rs, i) -> new LeavePeriod(rs.getLong("employee_id"), rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class)),
                businessTripId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        WorkCalendar.Snapshot calendar = workCalendar.snapshot(companyId);
        List<Object[]> rows = new ArrayList<>();
        for (LocalDate date = trip.startDate(); !date.isAfter(trip.endDate()); date = date.plusDays(1)) {
            if (calendar.isWorkday(date)) {
                rows.add(new Object[]{companyId, trip.employeeId(), Date.valueOf(date), businessTripId});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_method,
                                        business_trip_id)
                VALUES (?, ?, ?, 'ON_BUSINESS_TRIP', 'BUSINESS_TRIP', 'SYSTEM', ?)
                ON CONFLICT (employee_id, work_date) DO NOTHING
                """, rows);
    }

    /**
     * 휴가 취소가 승인될 때 같은 트랜잭션에서 부른다. 그 휴가로 만든 휴가 근태만 지운다 —
     * 건너뛴 날의 출근 기록이나, 정정으로 휴가 연결을 끊은 근태는 그대로 남는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteLeaveDays(long companyId, long leaveRequestId) {
        jdbc.update("DELETE FROM attendance WHERE company_id = ? AND leave_request_id = ? AND status = 'ON_VACATION'",
                companyId, leaveRequestId);
    }

    /**
     * 지금 화면이 다루는 근태 — 오늘 기록, 없으면 자정을 넘겨 이어지는 어제의 출근 상태 기록(어제 야간 끝 전까지).
     */
    private Optional<Attendance> current(LoginUser user, LocalDate today) {
        Optional<Attendance> todays = attendanceRepository.findByEmployeeIdAndWorkDate(user.employeeId(), today);
        if (todays.isPresent() || !todayStatusReader.yesterdayStillOpen(user.companyId())) {
            return todays;
        }
        return attendanceRepository.findByEmployeeIdAndWorkDate(user.employeeId(), today.minusDays(1))
                .filter(a -> a.getStatus() == AttendanceStatus.CHECKED_IN);
    }

    private static Attendance requireCheckedIn(Optional<Attendance> current) {
        Attendance attendance = current.orElseThrow(() -> new BusinessException(ErrorCode.ATT_NO_CHECK_IN));
        if (isLeaveOrTrip(attendance)) {
            throw new BusinessException(ErrorCode.ATT_ON_LEAVE_OR_TRIP);
        }
        return switch (attendance.getStatus()) {
            case CHECKED_IN -> attendance;
            case CHECKED_OUT -> throw new BusinessException(ErrorCode.ATT_ALREADY_CHECKED_OUT);
            default -> throw new BusinessException(ErrorCode.ATT_NO_CHECK_IN);
        };
    }

    private static boolean isLeaveOrTrip(Attendance attendance) {
        return attendance.getStatus() == AttendanceStatus.ON_VACATION
                || attendance.getStatus() == AttendanceStatus.ON_BUSINESS_TRIP;
    }

    private TodayAttendance view(LoginUser user, EmpStatus empStatus, Attendance attendance, LocalDate today) {
        LocalDate workDate = attendance != null ? attendance.getWorkDate() : today;
        boolean workday = workCalendar.isWorkday(user.companyId(), workDate);
        if (attendance == null) {
            return new TodayAttendance(workDate, workday, TodayStatusReader.resolve(empStatus, null, null), null, null,
                    null, null);
        }
        return new TodayAttendance(workDate, workday,
                TodayStatusReader.resolve(empStatus, attendance.getStatus(), attendance.getWorkType()),
                attendance.getWorkType(), attendance.getPlaceMemo(), seoul(attendance.getCheckInAt()),
                seoul(attendance.getCheckOutAt()));
    }

    /** 재직중만 출퇴근한다 — 휴직 중이면 거부한다(BR-ATT-003). 퇴직자는 로그인 단계에서 막힌다. */
    private EmpStatus requireActive(LoginUser user) {
        EmpStatus status = empStatus(user);
        if (status != EmpStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_ACTIVE);
        }
        return status;
    }

    private EmpStatus empStatus(LoginUser user) {
        return jdbc.query("SELECT status::text FROM employee WHERE id = ? AND company_id = ?",
                        (rs, i) -> EmpStatus.valueOf(rs.getString(1)), user.employeeId(), user.companyId())
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static void checkLocation(BigDecimal lat, BigDecimal lng, Map<String, String> errors) {
        if ((lat == null) != (lng == null)) {
            errors.put(lat == null ? "lat" : "lng", "위도와 경도는 함께 보내거나 함께 비우세요");
        }
    }

    private static WorkType parseWorkType(Object value) {
        return SELECTABLE_WORK_TYPES.stream().filter(t -> t.name().equals(value)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ENUM_VALUE,
                        Map.of("allowed", SELECTABLE_WORK_TYPES.stream().map(Enum::name).sorted().toList())));
    }

    private static String parsePlaceMemo(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.trim().length() > PLACE_MEMO_MAX_LENGTH) {
            throw BusinessException.invalidFields(
                    Map.of("placeMemo", PLACE_MEMO_MAX_LENGTH + "자 이하로 입력하세요"));
        }
        return blankToNull(text);
    }

    /** 기록 방식은 접속 기기로 판단한다 — 모바일 브라우저면 MOBILE, 그 밖에는 WEB(BR-ATT-007). */
    private static RecordMethod recordMethod(String userAgent) {
        return userAgent != null && MOBILE_USER_AGENT.matcher(userAgent).find() ? RecordMethod.MOBILE : RecordMethod.WEB;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static OffsetDateTime seoul(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }

    /** DB 는 소수 6자리까지 저장한다. */
    private static BigDecimal scaled(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(6, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** 휴가 · 출장 기간(근태 생성용). */
    private record LeavePeriod(long employeeId, LocalDate startDate, LocalDate endDate) {
    }
}
