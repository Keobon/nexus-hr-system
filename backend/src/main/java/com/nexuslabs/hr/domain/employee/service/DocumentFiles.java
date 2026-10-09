package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.dto.DocumentVersion;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.file.FileService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 직원 서류(F-EMP-08) · 회사 서류(F-COMP-07) 공통 규칙(BR-FILE-001) — 프로필 사진 · 로고 · 영수증도 같은 규칙(2026-10-10). 연결할 파일은 같은 회사 파일이면서 요청자가 올린 것이어야 하고,
 * 이미 다른 곳(서류 · 영수증 · 프로필 사진 · 로고)에 연결된 파일은 받지 않는다 — 내려받기 권한이 섞이지 않게 한다.
 */
@Component
public class DocumentFiles {

    /** 버전 행을 읽는 SELECT 목록. 별칭 d = 서류 테이블, f = file, u = 올린 직원. */
    public static final String VERSION_COLUMNS = """
            d.id, d.version_no, d.file_id, f.original_name, d.issued_date, d.expires_at, d.memo, d.uploaded_by,
            u.name AS uploaded_by_name, d.created_at, d.is_current, d.doc_type::text AS doc_type, d.doc_name""";

    private final FileService fileService;
    private final JdbcTemplate jdbc;

    public DocumentFiles(FileService fileService, JdbcTemplate jdbc) {
        this.fileService = fileService;
        this.jdbc = jdbc;
    }

    /** 문제가 있으면 VALIDATION_ERROR(error.fields.fileId). */
    public void requireLinkable(LoginUser user, long fileId) {
        requireLinkable(user, fileId, "fileId");
    }

    /** 같은 규칙을 다른 필드 이름으로 — 프로필 사진(profileFileId) · 회사 로고(logoFileId)도 이 규칙을 따른다. */
    public void requireLinkable(LoginUser user, long fileId, String field) {
        if (!uploadedBy(user, fileId)) {
            throw BusinessException.invalidFields(Map.of(field, "파일을 다시 올려 주세요"));
        }
        if (linked(user.companyId(), fileId, true)) {
            throw BusinessException.invalidFields(Map.of(field, "이미 다른 곳에 쓰인 파일입니다"));
        }
    }

    /** 요청자가 올린 같은 회사 파일인가. */
    public boolean uploadedBy(LoginUser user, long fileId) {
        return fileService.find(user.companyId(), fileId)
                .map(f -> f.uploadedBy() != null && f.uploadedBy() == user.employeeId())
                .orElse(false);
    }

    /**
     * 서류 · 영수증 · 프로필 사진 · 로고 중 어딘가에 이미 연결된 파일인가. countEndedClaims 가 false 면 반려 · 취소된
     * 경비 청구의 영수증은 세지 않는다(같은 영수증으로 다시 청구할 수 있게).
     */
    public boolean linked(long companyId, long fileId, boolean countEndedClaims) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM employee_document WHERE company_id = ? AND file_id = ?)
                            OR EXISTS (SELECT 1 FROM company_document WHERE company_id = ? AND file_id = ?)
                            OR EXISTS (SELECT 1 FROM expense_claim_line l
                                       JOIN expense_claim c ON c.id = l.expense_claim_id AND c.company_id = l.company_id
                                       WHERE l.company_id = ? AND l.receipt_file_id = ?
                                         AND (? OR c.status IN ('PENDING', 'APPROVED')))
                            OR EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND profile_file_id = ?)
                            OR EXISTS (SELECT 1 FROM company WHERE id = ? AND logo_file_id = ?)
                        """,
                Boolean.class, companyId, fileId, companyId, fileId, companyId, fileId, countEndedClaims, companyId,
                fileId, companyId, fileId));
    }

    /** 기타(OTHER)면 이름 필수(앞뒤 공백 제거), 다른 종류면 이름을 무시한다(null). */
    public static String docName(boolean other, String name) {
        if (!other) {
            return null;
        }
        if (name == null || name.isBlank()) {
            throw BusinessException.invalidFields(Map.of("docName", "서류 이름을 입력하세요"));
        }
        return name.trim();
    }

    public static void checkDates(LocalDate issuedDate, LocalDate expiresAt) {
        if (issuedDate != null && expiresAt != null && expiresAt.isBefore(issuedDate)) {
            throw BusinessException.invalidFields(Map.of("expiresAt", "만료일은 발급일 이후여야 합니다"));
        }
    }

    public static String memo(String memo) {
        return memo == null || memo.isBlank() ? null : memo.trim();
    }

    /** VERSION_COLUMNS 로 읽은 한 행. */
    public static DocumentVersion version(ResultSet rs, LocalDate today) throws SQLException {
        LocalDate expiresAt = rs.getObject("expires_at", LocalDate.class);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new DocumentVersion(rs.getLong("id"), rs.getInt("version_no"), rs.getLong("file_id"),
                rs.getString("original_name"), rs.getObject("issued_date", LocalDate.class), expiresAt,
                expiresAt != null && expiresAt.isBefore(today), rs.getString("memo"), rs.getLong("uploaded_by"),
                rs.getString("uploaded_by_name"), createdAt.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime());
    }
}
