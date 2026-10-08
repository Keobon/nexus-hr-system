package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.EmployeeDocType;

import java.util.List;

/** 직원 서류 종류 하나(OTHER 는 이름마다 하나) — 현재본과 이전 버전(최신순). */
public record EmployeeDocumentGroup(EmployeeDocType docType, String docName, DocumentVersion current,
                                    List<DocumentVersion> versions) {
}
