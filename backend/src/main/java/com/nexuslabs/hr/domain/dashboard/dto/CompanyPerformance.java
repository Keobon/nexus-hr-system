package com.nexuslabs.hr.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 성과 현황(F-DASH-06) — 한 평가 기간의 확정된 평가(퇴직자 제외). 홈 company.performance 와
 * GET /api/dashboard/company/performance 가 같은 모양이고, 기간 API 에만 availableCycles 가 붙는다.
 * average 는 총점 평균(소수 첫째 자리, 없으면 null). levelCounts 는 다섯 수준 모두(0 포함).
 */
public record CompanyPerformance(long evalCycleId, String cycleName, String cycleKind, int confirmedCount,
                                 BigDecimal average, Map<String, Integer> levelCounts, List<Ranked> top,
                                 List<Ranked> needsImprovement, List<OrgAverage> byOrgUnit,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) List<CycleOption> availableCycles) {

    /** rank 는 총점 높은 순(동점이면 사번 순), 1부터 겹치지 않는다. orgUnitName · jobGradeName 은 지금 소속 · 직급. */
    public record Ranked(int rank, long evaluationId, long employeeId, String employeeNo, String name,
                         String orgUnitName, String jobGradeName, BigDecimal totalScore, String level) {
    }

    /** 최상위 바로 아래 조직(하위 포함), 최상위에 바로 속한 사람은 최상위 이름으로 맨 뒤. 3명 미만이면 average null. */
    public record OrgAverage(long orgUnitId, String orgUnitName, int count, BigDecimal average) {
    }

    public record CycleOption(long evalCycleId, String cycleName, String cycleKind, OffsetDateTime closedAt) {
    }

    public CompanyPerformance withCycles(List<CycleOption> cycles) {
        return new CompanyPerformance(evalCycleId, cycleName, cycleKind, confirmedCount, average, levelCounts, top,
                needsImprovement, byOrgUnit, cycles);
    }
}
