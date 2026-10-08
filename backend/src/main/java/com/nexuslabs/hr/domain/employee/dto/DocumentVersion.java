package com.nexuslabs.hr.domain.employee.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 서류 한 버전(F-EMP-08 · F-COMP-07 공통). expired = 만료일이 오늘(서울)보다 앞섬.
 * 파일 본문은 GET /api/files/{fileId} 로 받는다(권한은 서류 쪽 FileAccessChecker 가 판단).
 */
public record DocumentVersion(long id, int versionNo, long fileId, String fileName, LocalDate issuedDate,
                              LocalDate expiresAt, boolean expired, String memo, long uploadedById,
                              String uploadedByName, OffsetDateTime createdAt) {
}
