package com.nexuslabs.hr.domain.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/pay-items/insurance-rates 의 항목 하나. current = 오늘 유효한 값(없으면 null),
 * upcoming = 적용 시작일이 미래인 행(빠른 순), history = 오늘까지 시작한 행 전체(최신순, current 포함).
 * 같은 적용 시작일 행이 여럿이면 나중에 만든 행이 정정본이고 앞 행은 superseded = true — PayVariableView 와 같다.
 */
public record InsuranceRateView(long payItemId, String name, Row current, List<Row> upcoming, List<Row> history) {

    public record Row(BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit, Long baseLowerLimit,
                      LocalDate effectiveFrom, OffsetDateTime createdAt, boolean superseded) {
    }
}
