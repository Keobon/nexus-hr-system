package com.nexuslabs.hr.domain.attendance.dto;

import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.entity.WorkType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 근태 소명 한 건(F-ATT-10). checkInAt · checkOutAt · workType 은 직원이 요청한 값(없으면 null).
 * 승인 응답에서 그 날짜의 귀속 월이 그사이 정산됐으면 warning = "PAY_MONTH_SETTLED"(정정은 됐고 명세서는 그대로), 그 밖에는 null.
 */
public record AttendanceExplanationResponse(long id, long employeeId, LocalDate workDate, String reason,
                                            OffsetDateTime checkInAt, OffsetDateTime checkOutAt, WorkType workType,
                                            RequestStatus status, String rejectReason, OffsetDateTime reviewedAt,
                                            OffsetDateTime createdAt, String warning) {
}
