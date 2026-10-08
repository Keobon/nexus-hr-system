package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 정산 한 건(확정 201 · GET /api/payroll-runs/{id} · 지급완료 응답). paystubs 는 명세서 요약(사원번호 순). */
public record PayrollRunView(long id, String payMonth, LocalDate payDate, PayrollStatus status,
                             OffsetDateTime confirmedAt, String confirmedByName, OffsetDateTime paidAt,
                             String paidByName, PayrollTotals totals, List<PaystubSummary> paystubs) {

    public record PaystubSummary(long paystubId, long employeeId, String employeeNo, String name, String orgUnitName,
                                 long grossPay, long totalDeduction, long netPay, long companyBurdenTotal) {
    }
}
