package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.domain.company.entity.CompanyDocType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/company/documents 본문(F-COMP-07). fileId 는 먼저 POST /api/files(purpose=COMPANY_DOCUMENT)로 올린 파일.
 * docName 은 OTHER 일 때 필수이고 다른 종류면 무시한다.
 */
public record CompanyDocumentRequest(
        @NotNull CompanyDocType docType,
        @Size(max = 100) String docName,
        @NotNull Long fileId,
        LocalDate issuedDate,
        LocalDate expiresAt,
        @Size(max = 255) String memo) {
}
