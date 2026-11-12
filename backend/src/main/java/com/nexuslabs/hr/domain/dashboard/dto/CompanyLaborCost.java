package com.nexuslabs.hr.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexuslabs.hr.global.response.PageResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 직원별 인건비(F-DASH-07) — 한 정산 월의 명세서 스냅샷(취소된 정산 제외). 인건비 = 총지급 + 회사부담.
 * 홈 company.laborCost 에는 top(상위 10명)만, GET /api/dashboard/company/labor-cost 에는 employees(페이지) ·
 * availableMonths 만 붙는다. changeRate 는 직전 정산 대비 %(소수 첫째 자리), 직전 정산이 없으면 null(키는 유지).
 */
public record CompanyLaborCost(String payMonth, int headcount, long total, BigDecimal changeRate,
                               @JsonInclude(JsonInclude.Include.NON_NULL) List<Row> top,
                               List<OrgCost> byOrgUnit,
                               @JsonInclude(JsonInclude.Include.NON_NULL) PageResponse<Row> employees,
                               @JsonInclude(JsonInclude.Include.NON_NULL) List<String> availableMonths) {

    /** orgUnitName 은 정산 당시 소속, jobGradeName 은 지금 직급(명세서에 직급 스냅샷이 없다). share 는 합계 대비 %. */
    public record Row(long employeeId, String employeeNo, String name, String orgUnitName, String jobGradeName,
                      long grossPay, long companyBurden, long laborCost, BigDecimal share) {
    }

    /** 정산 당시 소속을 지금 조직 트리로 올려 최상위 바로 아래 조직으로 묶는다. 최상위에 바로 속한 사람은 맨 뒤. */
    public record OrgCost(long orgUnitId, String orgUnitName, int headcount, long laborCost) {
    }
}
