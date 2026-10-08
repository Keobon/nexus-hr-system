package com.nexuslabs.hr.domain.payroll.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 조직별 예산 대비 인건비(GET /api/statistics/labor-cost, F-PAY-07). 예산이 있는 활성 조직마다, 그 조직과 하위 조직(지금의 트리)에
 * 정산 당시 소속이었던 직원의 총지급 + 회사부담. executionRate 는 % 소수 첫째 자리(예산 0 이면 null). 정산 전 월은 settled=false.
 */
public record LaborCost(String payMonth, boolean settled, List<Row> rows) {

    public record Row(long orgUnitId, String orgUnitName, long monthlyBudget, long laborCost, BigDecimal executionRate) {
    }
}
