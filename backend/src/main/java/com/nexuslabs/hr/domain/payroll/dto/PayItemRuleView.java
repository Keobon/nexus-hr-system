package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.PayRuleTarget;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/pay-items/{id}/rules 의 규칙(대상) 하나. current = 오늘 유효한 행(해제됐으면 amount null, 처음부터 미래면 null),
 * upcoming = 적용 시작일이 미래인 행(빠른 순), history = 오늘까지 시작한 행 전체(최신순). 같은 날짜면 앞 행은 superseded.
 */
public record PayItemRuleView(PayRuleTarget targetType, Long orgUnitId, String orgUnitName, boolean includeSubOrgs,
                              Long jobGradeId, String jobGradeName, boolean gradeOrAbove, Row current,
                              List<Row> upcoming, List<Row> history) {

    public record Row(Long amount, LocalDate effectiveFrom, OffsetDateTime createdAt, boolean superseded) {
    }
}
