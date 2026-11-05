package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 정산 월 목록 행(GET /api/payroll-runs) — 최근 귀속 월부터. PayrollRunView 에서 paystubs 를 뺀 모양.
 * 취소된 정산은 includeCancelled=true 일 때만 나오고 cancelledAt · cancelledByName · cancelReason 이 채워진다.
 */
public record PayrollRunRow(long id, String payMonth, LocalDate payDate, PayrollStatus status,
                            OffsetDateTime confirmedAt, String confirmedByName, OffsetDateTime paidAt,
                            String paidByName, OffsetDateTime cancelledAt, String cancelledByName,
                            String cancelReason, PayrollTotals totals) {
}
