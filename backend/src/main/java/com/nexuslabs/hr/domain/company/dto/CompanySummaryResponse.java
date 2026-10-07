package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.domain.company.entity.Company;

/** 화면 표시용 회사 정보 — 이름 · 로고 · 대표자명 · 주소 · 연락처. 모든 직원이 본다. */
public record CompanySummaryResponse(long id, String name, Long logoFileId, String ceoName, String address,
                                     String phone, String email) implements CompanyView {

    public static CompanySummaryResponse from(Company c) {
        return new CompanySummaryResponse(c.getId(), c.getName(), c.getLogoFileId(), c.getCeoName(), c.getAddress(),
                c.getPhone(), c.getEmail());
    }
}
