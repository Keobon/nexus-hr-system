package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.attendance.entity.AttendanceStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 오늘 상태 계산 순서(기능명세서 15.4, BR-ATT-001·003·005). DB 없이 계산만. */
class TodayStatusReaderTest {

    @Test
    void 재직상태가_먼저다() {
        assertThat(TodayStatusReader.resolve(EmpStatus.RESIGNED, AttendanceStatus.CHECKED_IN, WorkType.OFFICE)).isNull();
        assertThat(TodayStatusReader.resolve(EmpStatus.ON_LEAVE, null, null)).isEqualTo(TodayStatus.ON_LEAVE);
        assertThat(TodayStatusReader.resolve(EmpStatus.ON_LEAVE, AttendanceStatus.CHECKED_IN, WorkType.REMOTE))
                .isEqualTo(TodayStatus.ON_LEAVE);
    }

    @Test
    void 근태가_없으면_출근_전이고_휴가_출장_퇴근은_그대로다() {
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, null, null)).isEqualTo(TodayStatus.BEFORE_WORK);
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, AttendanceStatus.ON_VACATION, null))
                .isEqualTo(TodayStatus.ON_VACATION);
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, AttendanceStatus.ON_BUSINESS_TRIP, WorkType.BUSINESS_TRIP))
                .isEqualTo(TodayStatus.BUSINESS_TRIP);
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, AttendanceStatus.CHECKED_OUT, WorkType.REMOTE))
                .isEqualTo(TodayStatus.OFF_WORK);
    }

    @Test
    void 출근_중이면_근무_형태가_오늘_상태다() {
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, AttendanceStatus.CHECKED_IN, WorkType.OFFICE))
                .isEqualTo(TodayStatus.OFFICE);
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, AttendanceStatus.CHECKED_IN, WorkType.REMOTE))
                .isEqualTo(TodayStatus.REMOTE);
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, AttendanceStatus.CHECKED_IN, WorkType.FIELD))
                .isEqualTo(TodayStatus.FIELD);
        assertThat(TodayStatusReader.resolve(EmpStatus.ACTIVE, AttendanceStatus.MISSING_CHECKOUT, null))
                .isEqualTo(TodayStatus.OFFICE);
    }
}
