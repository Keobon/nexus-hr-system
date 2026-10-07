package com.nexuslabs.hr.domain.company.dto;

/** GET /api/company 의 응답. COMPANY_MANAGE 가 있으면 전체(CompanyDetailResponse), 없으면 표시용(CompanySummaryResponse). */
public sealed interface CompanyView permits CompanyDetailResponse, CompanySummaryResponse {
}
