package com.nexuslabs.hr.domain.attendance.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.leave.entity.LeaveRequest;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 근태. 출근할 때 또는 휴가·출장이 승인될 때 생긴다. 지각·근무·연장 시간은 저장하지 않는다(BR-WORK-002). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "attendance_status")
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "work_type")
    private WorkType workType;

    private OffsetDateTime checkInAt;
    private OffsetDateTime checkOutAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "record_method")
    private RecordMethod checkInMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "record_method")
    private RecordMethod checkOutMethod;

    private BigDecimal checkInLat;
    private BigDecimal checkInLng;
    private BigDecimal checkOutLat;
    private BigDecimal checkOutLng;
    private String placeMemo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_request_id")
    private LeaveRequest leaveRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_trip_id")
    private BusinessTrip businessTrip;

    private String correctionReason;
    private Long correctedBy;
    private OffsetDateTime correctedAt;

    public Attendance(Employee employee, LocalDate workDate, AttendanceStatus status) {
        this.employee = employee;
        this.workDate = workDate;
        this.status = status;
    }

    /** 출근(F-ATT-01). 위치는 브라우저가 허용했을 때만 있다. */
    public static Attendance checkIn(Employee employee, LocalDate workDate, WorkType workType, String placeMemo,
                                     OffsetDateTime at, RecordMethod method, BigDecimal lat, BigDecimal lng) {
        Attendance attendance = new Attendance(employee, workDate, AttendanceStatus.CHECKED_IN);
        attendance.workType = workType;
        attendance.placeMemo = placeMemo;
        attendance.checkInAt = at;
        attendance.checkInMethod = method;
        attendance.checkInLat = lat;
        attendance.checkInLng = lng;
        return attendance;
    }

    /** 퇴근(F-ATT-02). 출근 상태인지는 서비스가 먼저 확인한다. */
    public void checkOut(OffsetDateTime at, RecordMethod method, BigDecimal lat, BigDecimal lng) {
        this.status = AttendanceStatus.CHECKED_OUT;
        this.checkOutAt = at;
        this.checkOutMethod = method;
        this.checkOutLat = lat;
        this.checkOutLng = lng;
    }

    /** 퇴근 전까지 본인이 근무 형태를 바꾼다 — 마지막 값이 그날 근무 형태다(F-ATT-01). */
    public void changeWorkType(WorkType workType, String placeMemo) {
        this.workType = workType;
        this.placeMemo = placeMemo;
    }
}
