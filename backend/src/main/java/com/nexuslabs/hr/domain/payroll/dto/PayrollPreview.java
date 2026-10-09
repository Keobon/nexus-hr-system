package com.nexuslabs.hr.domain.payroll.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 정산 미리보기(POST /api/payroll-runs/preview) — 저장하지 않는다. 귀속 월이 끝나기 전이면 warning = "PAY_MONTH_NOT_ENDED"
 * (남은 날의 근태 · 결근이 빠진다 — 확정은 월이 끝난 뒤에만 된다). unregistered = 그달에 유효한 기본 급여가 없는 대상 직원.
 * negativeNetPayEmployeeIds = 공제가 지급보다 많아 실지급액이 음수인 직원 — 비어 있지 않으면 확정이 거부된다.
 */
public record PayrollPreview(String payMonth, LocalDate payDate, String warning, List<EmployeePayroll> employees,
                             List<Unregistered> unregistered, List<Long> negativeNetPayEmployeeIds,
                             PayrollTotals totals) {

    public record Unregistered(long employeeId, String employeeNo, String name, String reason) {
    }
}
