package com.nexuslabs.hr.domain.org.dto;

/** 조직장 요약. 조직장이 없으면 응답의 lead 가 null 이다("조직장 미지정"). */
public record LeadSummary(long id, String name) {
}
