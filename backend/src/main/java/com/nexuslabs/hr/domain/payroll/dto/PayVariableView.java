package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayVarCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/pay-variables 의 변수 하나(API 설계서 10.1). current = 오늘 유효한 값(없으면 null),
 * upcoming = 적용 시작일이 미래인 행(빠른 순), history = 오늘까지 시작한 행 전체(최신순, current 포함).
 * 같은 적용 시작일 행이 여럿이면 나중에 만든 행이 정정본이고 앞 행은 superseded = true(역할 분담 v2 2.3 F-J1).
 */
public record PayVariableView(PayVarCode varCode, Row current, List<Row> upcoming, List<Row> history) {

    public record Row(BigDecimal value, LocalDate effectiveFrom, OffsetDateTime createdAt, boolean superseded) {
    }
}
