package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.EmployeeDocType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/employees/{id}/documents 본문(F-EMP-08). fileId 는 먼저 POST /api/files(purpose=EMPLOYEE_DOCUMENT)로 올린 파일.
 * docName 은 OTHER 일 때 필수이고 다른 종류면 무시한다.
 */
public record EmployeeDocumentRequest(
        @NotNull EmployeeDocType docType,
        @Size(max = 100) String docName,
        @NotNull Long fileId,
        LocalDate issuedDate,
        LocalDate expiresAt,
        @Size(max = 255) String memo) {
}
