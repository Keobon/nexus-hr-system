package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.domain.company.entity.CompanyDocType;
import com.nexuslabs.hr.domain.company.entity.CompanyField;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 회사 정보 변경 이력 한 줄(GET /api/company/change-history). 근거 서류가 없으면 document 가 null 이다. */
public record CompanyChangeHistoryItem(long id, CompanyField field, String oldValue, String newValue,
                                       LocalDate effectiveDate, String reason, Document document, ChangedBy changedBy,
                                       OffsetDateTime createdAt) {

    public record Document(long id, CompanyDocType docType, String docName) {
    }

    public record ChangedBy(long id, String name) {
    }
}
