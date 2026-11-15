package com.nexuslabs.hr.domain.turnover.dto;

import com.nexuslabs.hr.domain.employee.entity.ResignReason;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 퇴사 통계 응답(작업 지시서 2-4, GET /api/stats/turnover/*). 퇴사율 · 비율은 % 소수 첫째 자리, 평균 재직 인원은 소수 첫째 자리.
 * 재직(D) = 입사일 <= D 이고 (퇴사일 없음 또는 퇴사일 > D). 평균 재직 = (기간 첫날 재직 + 말일 재직) ÷ 2,
 * 퇴사율 = 기간 퇴사자 ÷ 평균 재직 × 100(평균 0 이면 0).
 */
public final class TurnoverStats {

    private TurnoverStats() {
    }

    /** headcount 는 그달 말일 재직 인원, netChange = 입사 − 퇴사. */
    public record Summary(String month, int headcount, int hires, int resignations, BigDecimal turnoverRate,
                          BigDecimal prevTurnoverRate, int netChange) {
    }

    /** period: 2026-03 · 2026-Q1 · 2026. */
    public record TrendPoint(String period, int hires, int resignations, BigDecimal avgHeadcount,
                             BigDecimal turnoverRate) {
    }

    public record ByDepartment(long orgUnitId, String orgUnitName, BigDecimal avgHeadcount, int resignations,
                               BigDecimal turnoverRate) {
    }

    /** bucket: UNDER_1Y · Y1_3 · Y3_5 · OVER_5Y — 근속 = 퇴사일 − 입사일. */
    public record ByTenure(String bucket, int count) {
    }

    public record ByReason(ResignReason reason, int count, BigDecimal ratio) {
    }

    /** name 은 EMPLOYEE_MANAGE 가 없으면 가린다(홍*동 · 이*). orgUnitName · jobGradeName 은 퇴사 당시(= 지금 기록된) 소속 · 직급. */
    public record Resignation(long employeeId, String name, String orgUnitName, String jobGradeName,
                              LocalDate hireDate, LocalDate resignDate, long tenureDays, ResignReason resignReason) {
    }
}
