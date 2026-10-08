package com.nexuslabs.hr.domain.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 급여명세서(F-PAY-06) — 회사 · 직원 머리말과 금액은 정산 당시 저장된 값이다. 본인 명세서(/api/me/payslips/{id})에는
 * companyBurdenTotal 과 줄의 companyAmount 필드가 없다. bankAccount 는 가린 계좌("신한 ***-****-1234"), 없으면 null.
 */
public record PayslipView(long id, long payrollRunId, String payMonth, LocalDate payDate, PayrollStatus status,
                          CompanySnap company, long employeeId, String employeeNo, String name, String orgUnitName,
                          String bankAccount, long basePay, long ordinaryHourlyWage, int workedDays, int monthDays,
                          int dependentsCount, int childrenCount, List<PayslipLine> lines, long grossPay,
                          long taxablePay, long incomeTax, long localIncomeTax, long totalDeduction, long netPay,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Optional<Long> companyBurdenTotal,
                          List<Long> expenseClaimIds) {

    public record CompanySnap(String name, String businessRegNo, String ceoName, String address) {
    }

    /** 본인 명세서 — 회사부담 합계와 줄의 회사부담을 뺀다. */
    public PayslipView forEmployee() {
        return new PayslipView(id, payrollRunId, payMonth, payDate, status, company, employeeId, employeeNo, name,
                orgUnitName, bankAccount, basePay, ordinaryHourlyWage, workedDays, monthDays, dependentsCount,
                childrenCount, lines.stream().map(PayslipLine::withoutCompanyAmount).toList(), grossPay, taxablePay,
                incomeTax, localIncomeTax, totalDeduction, netPay, null, expenseClaimIds);
    }
}
