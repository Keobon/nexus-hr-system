package com.nexuslabs.hr.domain.leave.entity;

/**
 * 반차(2026-11-15) — DB ENUM half_day 와 이름이 같아야 한다. 0.5일, 하루짜리. 반나절 = 그날 1일 소정근로시간의 절반.
 * 오전 반차는 오후에 일한다(출근 기준 = 시작 + 반나절 + 휴게), 오후 반차는 오전에 일한다(퇴근 기준 = 시작 + 반나절).
 */
public enum HalfDay {
    AM, PM
}
