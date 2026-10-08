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
 * 직원 서류(F-EMP-08) · 회사 서류(F-COMP-07) 공통 규칙(BR-FILE-001). 연결할 파일은 같은 회사 파일이면서 요청자가 올린 것이어야 하고,
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
        long cid = user.companyId();
        boolean mine = fileService.find(cid, fileId)
                .map(f -> f.uploadedBy() != null && f.uploadedBy() == user.employeeId())
                .orElse(false);
        if (!mine) {
            throw BusinessException.invalidFields(Map.of("fileId", "파일을 다시 올려 주세요"));
        }
        Boolean linked = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM employee_document WHERE company_id = ? AND file_id = ?)
                            OR EXISTS (SELECT 1 FROM company_document WHERE company_id = ? AND file_id = ?)
                            OR EXISTS (SELECT 1 FROM expense_claim_line WHERE company_id = ? AND receipt_file_id = ?)
                            OR EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND profile_file_id = ?)
                            OR EXISTS (SELECT 1 FROM company WHERE id = ? AND logo_file_id = ?)
                        """,
                Boolean.class, cid, fileId, cid, fileId, cid, fileId, cid, fileId, cid, fileId);
        if (Boolean.TRUE.equals(linked)) {
            throw BusinessException.invalidFields(Map.of("fileId", "이미 다른 곳에 쓰인 파일입니다"));
        }
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
