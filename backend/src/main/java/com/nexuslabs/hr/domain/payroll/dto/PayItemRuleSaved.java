package com.nexuslabs.hr.domain.payroll.dto;

import java.util.List;

/** 규칙 등록 응답(201) — 그 항목의 규칙 목록. warning 은 직원별 항목 등록과 같다(정산된 월에 걸치면 PAY_MONTH_SETTLED). */
public record PayItemRuleSaved(List<PayItemRuleView> rules, String warning) {
}
