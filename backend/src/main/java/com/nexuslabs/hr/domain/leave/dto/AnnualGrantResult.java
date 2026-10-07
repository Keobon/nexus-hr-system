package com.nexuslabs.hr.domain.leave.dto;

/** 연도 일괄 부여 결과. skipped = 정기·입사 부여가 이미 있어 건너뛴 건수. */
public record AnnualGrantResult(int leaveYear, int created, int skipped) {
}
