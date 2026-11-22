package com.nexuslabs.hr.domain.notification.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepActivated;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.global.config.ClockConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 결재 알림 문구 — 서버가 고정 형식으로 만든다(AI 를 쓰지 않는다). 신청 사유 원문은 넣지 않는다.
 * <ul>
 *   <li>제목: "휴가 결재가 올라왔습니다" (업무 이름만 바뀐다)</li>
 *   <li>본문(휴가 · 휴가 취소): "정수아 · 연차 · 11/3(화) 1일", "… · 11/3(화)~11/5(목) 3일", "… · 11/3(화) 오후 반차"</li>
 *   <li>본문(연장근무): "정수아 · 연장근무 · 11/18(화) 19:00~22:00 (3시간)" — 사유 원문은 넣지 않는다</li>
 *   <li>본문(출장): "정수아 · 출장 · 부산 · 11/20(금)~11/21(토)" — 목적 원문은 넣지 않는다</li>
 *   <li>본문(그 밖의 업무, 신청을 못 찾을 때): "정수아 · 출장 경비"</li>
 * </ul>
 * 회사는 이벤트의 companyId 로 정한다(모든 쿼리에 company_id 조건).
 */
@Component
public class NotificationMessages {

    private static final int BODY_MAX = 300;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;

    public NotificationMessages(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String title(ApprovalWorkType workType) {
        return workName(workType) + " 결재가 올라왔습니다";
    }

    public String body(ApprovalStepActivated e) {
        String applicant = jdbc.query("SELECT name FROM employee WHERE id = ? AND company_id = ?",
                (rs, i) -> rs.getString("name"), e.applicantEmployeeId(), e.companyId()).stream().findFirst()
                .orElse("신청자");
        String detail = switch (e.workType()) {
            case LEAVE, LEAVE_CANCEL -> leave(e.companyId(), e.targetId());
            case OVERTIME -> overtime(e.companyId(), e.targetId());
            case BUSINESS_TRIP -> trip(e.companyId(), e.targetId());
            default -> null;
        };
        String body = applicant + " · " + (detail != null ? detail : workName(e.workType()));
        return body.length() > BODY_MAX ? body.substring(0, BODY_MAX) : body;
    }

    /** "연차 · 11/3(화) 1일". 휴가가 없으면 null(일반 문구로). */
    private String leave(long companyId, long leaveRequestId) {
        return jdbc.query("""
                        SELECT t.name AS type_name, r.start_date, r.end_date, r.days, r.half_day::text AS half_day
                        FROM leave_request r
                        JOIN leave_type t ON t.id = r.leave_type_id AND t.company_id = r.company_id
                        WHERE r.id = ? AND r.company_id = ?
                        """,
                (rs, i) -> {
                    LocalDate start = rs.getObject("start_date", LocalDate.class);
                    LocalDate end = rs.getObject("end_date", LocalDate.class);
                    String halfDay = rs.getString("half_day");
                    String period;
                    if (halfDay != null) {
                        period = day(start) + ("AM".equals(halfDay) ? " 오전 반차" : " 오후 반차");
                    } else {
                        period = (start.equals(end) ? day(start) : day(start) + "~" + day(end)) + " "
                                + days(rs.getBigDecimal("days")) + "일";
                    }
                    return rs.getString("type_name") + " · " + period;
                },
                leaveRequestId, companyId).stream().findFirst().orElse(null);
    }

    /** "연장근무 · 11/18(화) 19:00~22:00 (3시간)". 신청이 없으면 null(일반 문구로). 사유 원문은 넣지 않는다. */
    private String overtime(long companyId, long overtimeRequestId) {
        return jdbc.query("""
                        SELECT work_date, planned_start, planned_end, requested_minutes
                        FROM overtime_request WHERE id = ? AND company_id = ?
                        """,
                (rs, i) -> {
                    OffsetDateTime start = seoul(rs.getObject("planned_start", OffsetDateTime.class));
                    OffsetDateTime end = seoul(rs.getObject("planned_end", OffsetDateTime.class));
                    return "연장근무 · " + day(rs.getObject("work_date", LocalDate.class)) + " "
                            + start.format(HH_MM) + "~" + end.format(HH_MM)
                            + " (" + hours(rs.getInt("requested_minutes")) + ")";
                },
                overtimeRequestId, companyId).stream().findFirst().orElse(null);
    }

    /** "출장 · 부산 · 11/20(금)~11/21(토)", 하루면 "… · 11/20(금)". 신청이 없으면 null. 목적 원문은 넣지 않는다. */
    private String trip(long companyId, long businessTripId) {
        return jdbc.query("SELECT destination, start_date, end_date FROM business_trip WHERE id = ? AND company_id = ?",
                (rs, i) -> {
                    LocalDate start = rs.getObject("start_date", LocalDate.class);
                    LocalDate end = rs.getObject("end_date", LocalDate.class);
                    return "출장 · " + rs.getString("destination") + " · "
                            + (start.equals(end) ? day(start) : day(start) + "~" + day(end));
                },
                businessTripId, companyId).stream().findFirst().orElse(null);
    }

    /** 180 → "3시간", 150 → "2시간 30분", 40 → "40분". */
    private static String hours(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return m + "분";
        }
        return m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }

    private static String day(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth() + "("
                + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }

    private static String days(BigDecimal days) {
        return days.stripTrailingZeros().toPlainString();
    }

    private static String workName(ApprovalWorkType workType) {
        return switch (workType) {
            case LEAVE -> "휴가";
            case LEAVE_CANCEL -> "휴가 취소";
            case OVERTIME -> "연장근무";
            case BUSINESS_TRIP -> "출장";
            case TRIP_EXPENSE -> "출장 경비";
        };
    }
}
