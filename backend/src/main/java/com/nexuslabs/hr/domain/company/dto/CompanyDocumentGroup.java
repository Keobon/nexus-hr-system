package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.domain.company.entity.CompanyDocType;
import com.nexuslabs.hr.domain.employee.dto.DocumentVersion;

import java.util.List;

/** 회사 서류 종류 하나(OTHER 는 이름마다 하나) — 현재본과 이전 버전(최신순). 직원 서류와 같은 모양. */
public record CompanyDocumentGroup(CompanyDocType docType, String docName, DocumentVersion current,
                                   List<DocumentVersion> versions) {
}
