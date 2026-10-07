package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.domain.company.entity.Company;

import java.time.LocalDate;

/** 회사 정보 전체(F-COMP-02). COMPANY_MANAGE 가 있을 때의 조회 응답이자 수정 응답. */
public record CompanyDetailResponse(long id, String name, String nameEn, String businessRegNo, String corpRegNo,
                                    String ceoName, String address, String phone, String fax, String email,
                                    String website, String businessType, String businessItem, LocalDate foundedDate,
                                    Long logoFileId, int payDay, String employeeNoPrefix, int fiscalYearStartMonth,
                                    boolean setupCompleted) implements CompanyView {

    public static CompanyDetailResponse from(Company c) {
        return new CompanyDetailResponse(c.getId(), c.getName(), c.getNameEn(), c.getBusinessRegNo(), c.getCorpRegNo(),
                c.getCeoName(), c.getAddress(), c.getPhone(), c.getFax(), c.getEmail(), c.getWebsite(),
                c.getBusinessType(), c.getBusinessItem(), c.getFoundedDate(), c.getLogoFileId(), c.getPayDay(),
                c.getEmployeeNoPrefix(), c.getFiscalYearStartMonth(), c.isSetupCompleted());
    }
}
