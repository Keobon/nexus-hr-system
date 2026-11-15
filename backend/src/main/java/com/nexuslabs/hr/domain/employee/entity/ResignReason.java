package com.nexuslabs.hr.domain.employee.entity;

/** 퇴사 사유 — DB ENUM resign_reason 과 이름이 같아야 한다(2026-11-15). 이직 · 개인 사정 · 계약 만료 · 정년 · 권고사직/해고 · 기타. */
public enum ResignReason {
    JOB_CHANGE, PERSONAL, CONTRACT_END, RETIREMENT, DISMISSAL, ETC
}
