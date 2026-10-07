package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.company.dto.CompanyChangeHistoryItem;
import com.nexuslabs.hr.domain.company.dto.CompanyDetailResponse;
import com.nexuslabs.hr.domain.company.dto.CompanySummaryResponse;
import com.nexuslabs.hr.domain.company.dto.CompanyView;
import com.nexuslabs.hr.domain.company.entity.Company;
import com.nexuslabs.hr.domain.company.entity.CompanyChangeHistory;
import com.nexuslabs.hr.domain.company.entity.CompanyDocType;
import com.nexuslabs.hr.domain.company.entity.CompanyDocument;
import com.nexuslabs.hr.domain.company.entity.CompanyField;
import com.nexuslabs.hr.domain.company.repository.CompanyChangeHistoryRepository;
import com.nexuslabs.hr.domain.company.repository.CompanyDocumentRepository;
import com.nexuslabs.hr.domain.company.repository.CompanyRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.file.FileService;
import com.nexuslabs.hr.global.file.StoredFile;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import com.nexuslabs.hr.global.request.PatchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * 회사 기본정보 조회·수정과 변경 이력(F-COMP-02).
 * 회사명 · 대표자명 · 사업자등록번호 · 주소를 바꾸면 항목마다 변경 이력을 한 줄씩 남긴다(BR-TEN-004, append-only).
 * COMPANY 는 company_id 가 없는 유일한 테이블이라 @TenantId 필터가 없다 — 항상 토큰의 회사 ID 로만 읽는다.
 */
@Service
public class CompanyService {

    private static final Set<String> PATCH_FIELDS = Set.of("name", "nameEn", "businessRegNo", "corpRegNo", "ceoName",
            "address", "phone", "fax", "email", "website", "businessType", "businessItem", "foundedDate", "logoFileId",
            "payDay", "employeeNoPrefix", "fiscalYearStartMonth", "change");
    private static final Set<String> NOT_NULL_FIELDS = Set.of("name", "businessRegNo", "ceoName", "address", "phone",
            "email", "payDay", "fiscalYearStartMonth");

    private static final Pattern BUSINESS_REG_NO = Pattern.compile("^\\d{3}-?\\d{2}-?\\d{5}$");
    private static final Pattern CORP_REG_NO = Pattern.compile("^\\d{6}-?\\d{7}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern EMPLOYEE_NO_PREFIX = Pattern.compile("^[A-Za-z0-9-]{1,10}$");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final CompanyRepository companyRepository;
    private final CompanyChangeHistoryRepository changeHistoryRepository;
    private final CompanyDocumentRepository documentRepository;
    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;
    private final FileService fileService;
    private final AuditLogger auditLogger;

    public CompanyService(CompanyRepository companyRepository, CompanyChangeHistoryRepository changeHistoryRepository,
                          CompanyDocumentRepository documentRepository, JdbcTemplate jdbc,
                          PermissionReader permissionReader, FileService fileService, AuditLogger auditLogger) {
        this.companyRepository = companyRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.documentRepository = documentRepository;
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
        this.fileService = fileService;
        this.auditLogger = auditLogger;
    }

    /** COMPANY_MANAGE 가 있으면 전체 필드, 아니면 화면 표시용(이름 · 로고 · 대표자명 · 주소 · 연락처)만. */
    @Transactional(readOnly = true)
    public CompanyView get(LoginUser user) {
        Company company = company(user);
        return permissionReader.permissionsOf(user).containsKey(PermissionCode.COMPANY_MANAGE)
                ? CompanyDetailResponse.from(company) : CompanySummaryResponse.from(company);
    }

    /**
     * 수정. 보낸 필드만 바꾸고 null 은 비운다(API 설계서 1.1).
     * 회사명 · 대표자명 · 사업자등록번호 · 주소 중 값이 실제로 달라지는 것이 있으면 change(효력일 · 사유 · 근거 서류)가 필수다.
     */
    @Transactional
    public CompanyDetailResponse update(LoginUser user, Map<String, Object> patch) {
        PatchRequest.check(patch, PATCH_FIELDS, NOT_NULL_FIELDS);
        Company company = company(user);
        CompanyDetailResponse before = CompanyDetailResponse.from(company);
        Reader in = new Reader(patch);

        String name = in.text("name", before.name(), 100);
        String businessRegNo = in.formatted("businessRegNo", before.businessRegNo(), 12, BUSINESS_REG_NO,
                "사업자등록번호는 숫자 10자리입니다", CompanyRegistrationService::formatBusinessRegNo);
        String ceoName = in.text("ceoName", before.ceoName(), 50);
        String address = in.text("address", before.address(), 255);
        String phone = in.text("phone", before.phone(), 20);
        String fax = in.text("fax", before.fax(), 20);
        String email = in.formatted("email", before.email(), 100, EMAIL, "이메일 형식이 아닙니다", s -> s);
        String website = in.text("website", before.website(), 255);
        String nameEn = in.text("nameEn", before.nameEn(), 100);
        String corpRegNo = in.formatted("corpRegNo", before.corpRegNo(), 14, CORP_REG_NO,
                "법인등록번호는 숫자 13자리입니다", CompanyService::formatCorpRegNo);
        String businessType = in.text("businessType", before.businessType(), 100);
        String businessItem = in.text("businessItem", before.businessItem(), 100);
        LocalDate foundedDate = in.date("foundedDate", before.foundedDate());
        Long logoFileId = in.id("logoFileId", before.logoFileId());
        int payDay = in.range("payDay", before.payDay(), 1, 31);
        String employeeNoPrefix = in.formatted("employeeNoPrefix", before.employeeNoPrefix(), 10,
                EMPLOYEE_NO_PREFIX, "영문 · 숫자 · 하이픈 10자 이내로 입력하세요", s -> s);
        int fiscalYearStartMonth = in.range("fiscalYearStartMonth", before.fiscalYearStartMonth(), 1, 12);

        Map<CompanyField, String[]> tracked = new LinkedHashMap<>();
        track(tracked, CompanyField.NAME, before.name(), name);
        track(tracked, CompanyField.CEO_NAME, before.ceoName(), ceoName);
        track(tracked, CompanyField.BUSINESS_REG_NO, before.businessRegNo(), businessRegNo);
        track(tracked, CompanyField.ADDRESS, before.address(), address);
        Change change = tracked.isEmpty() ? null : in.change();
        in.throwIfInvalid();

        if (!Objects.equals(businessRegNo, before.businessRegNo()) && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM company WHERE business_reg_no = ? AND id <> ?)",
                Boolean.class, businessRegNo, company.getId()))) {
            throw new BusinessException(ErrorCode.BUSINESS_REG_NO_DUPLICATE);
        }
        if (fiscalYearStartMonth != before.fiscalYearStartMonth() && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM leave_grant WHERE company_id = ?)", Boolean.class, company.getId()))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "휴가가 부여된 뒤에는 회계연도 시작월을 바꿀 수 없습니다");
        }
        if (logoFileId != null && !logoFileId.equals(before.logoFileId())) {
            requireImage(user.companyId(), logoFileId);
        }
        CompanyDocument document = change == null || change.documentId() == null ? null
                : documentRepository.findById(change.documentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "근거 서류를 찾을 수 없습니다"));

        company.changeIdentity(name, businessRegNo, ceoName, address);
        company.changeContact(phone, fax, email, website);
        company.changeDetails(nameEn, corpRegNo, businessType, businessItem, foundedDate);
        company.changeLogo(logoFileId);
        company.changeSettings((short) payDay, employeeNoPrefix, (short) fiscalYearStartMonth);
        tracked.forEach((field, values) -> changeHistoryRepository.save(new CompanyChangeHistory(field, values[0],
                values[1], change.effectiveDate(), change.reason(), document, user.employeeId())));

        CompanyDetailResponse after = CompanyDetailResponse.from(company);
        auditLogger.log(user, AuditAction.UPDATE, "COMPANY", company.getId(), before, after);
        return after;
    }

    /** 변경 이력, 최신순. */
    @Transactional(readOnly = true)
    public List<CompanyChangeHistoryItem> changeHistory(LoginUser user) {
        return jdbc.query("""
                        SELECT h.id, h.field::text AS field, h.old_value, h.new_value, h.effective_date, h.reason,
                               h.document_id, d.doc_type::text AS doc_type, d.doc_name, h.changed_by,
                               e.name AS changed_by_name, h.created_at
                        FROM company_change_history h
                             JOIN employee e ON e.id = h.changed_by AND e.company_id = h.company_id
                             LEFT JOIN company_document d ON d.id = h.document_id AND d.company_id = h.company_id
                        WHERE h.company_id = ?
                        ORDER BY h.created_at DESC, h.id DESC
                        """,
                (rs, i) -> new CompanyChangeHistoryItem(rs.getLong("id"), CompanyField.valueOf(rs.getString("field")),
                        rs.getString("old_value"), rs.getString("new_value"),
                        rs.getObject("effective_date", LocalDate.class), rs.getString("reason"),
                        rs.getObject("document_id") == null ? null : new CompanyChangeHistoryItem.Document(
                                rs.getLong("document_id"), CompanyDocType.valueOf(rs.getString("doc_type")),
                                rs.getString("doc_name")),
                        new CompanyChangeHistoryItem.ChangedBy(rs.getLong("changed_by"), rs.getString("changed_by_name")),
                        rs.getObject("created_at", OffsetDateTime.class)),
                user.companyId());
    }

    private Company company(LoginUser user) {
        return companyRepository.findById(user.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireImage(long companyId, long fileId) {
        StoredFile file = fileService.find(companyId, fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다"));
        if (!IMAGE_TYPES.contains(file.contentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "로고는 jpg, png만 쓸 수 있습니다");
        }
    }

    private static void track(Map<CompanyField, String[]> tracked, CompanyField field, String before, String after) {
        if (!Objects.equals(before, after)) {
            tracked.put(field, new String[]{before, after});
        }
    }

    private static String formatCorpRegNo(String input) {
        String digits = input.replaceAll("\\D", "");
        return digits.substring(0, 6) + "-" + digits.substring(6);
    }

    private record Change(LocalDate effectiveDate, String reason, Long documentId) {
    }

    /** PATCH 본문에서 값을 꺼낸다. 안 보낸 필드는 지금 값을 그대로 돌려주고, 잘못된 값은 모아 두었다가 한 번에 알린다. */
    private static final class Reader {

        private final Map<String, Object> patch;
        private final Map<String, String> errors = new LinkedHashMap<>();

        Reader(Map<String, Object> patch) {
            this.patch = patch;
        }

        String text(String field, String current, int maxLength) {
            return formatted(field, current, maxLength, null, null, s -> s);
        }

        /** 형식(pattern)이 맞으면 저장 형식으로 바꿔(formatter) 돌려준다. */
        String formatted(String field, String current, int maxLength, Pattern pattern, String message,
                         UnaryOperator<String> formatter) {
            if (!patch.containsKey(field)) {
                return current;
            }
            Object value = patch.get(field);
            if (value == null) {
                return null;
            }
            if (!(value instanceof String text)) {
                errors.put(field, "문자로 입력하세요");
                return current;
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                if (NOT_NULL_FIELDS.contains(field)) {
                    errors.put(field, "필수 항목입니다");
                    return current;
                }
                return null;
            }
            if (trimmed.length() > maxLength) {
                errors.put(field, maxLength + "자 이하로 입력하세요");
                return current;
            }
            if (pattern != null && !pattern.matcher(trimmed).matches()) {
                errors.put(field, message);
                return current;
            }
            return formatter.apply(trimmed);
        }

        LocalDate date(String field, LocalDate current) {
            if (!patch.containsKey(field)) {
                return current;
            }
            return patch.get(field) == null ? null : parseDate(field, patch.get(field), current);
        }

        Long id(String field, Long current) {
            if (!patch.containsKey(field)) {
                return current;
            }
            Object value = patch.get(field);
            if (value == null) {
                return null;
            }
            if (value instanceof Integer || value instanceof Long) {
                return ((Number) value).longValue();
            }
            errors.put(field, "다시 선택하세요");
            return current;
        }

        int range(String field, int current, int min, int max) {
            if (!patch.containsKey(field)) {
                return current;
            }
            if (patch.get(field) instanceof Integer value && value >= min && value <= max) {
                return value;
            }
            errors.put(field, min + "~" + max + " 사이의 숫자로 입력하세요");
            return current;
        }

        /** 변경 이력에 남길 효력일 · 사유 · 근거 서류. 네 항목 중 하나라도 달라질 때만 부른다. */
        Change change() {
            if (!(patch.get("change") instanceof Map<?, ?> change)) {
                errors.put("change", "회사명 · 대표자명 · 사업자등록번호 · 주소를 바꾸려면 효력일과 사유를 입력하세요");
                return null;
            }
            LocalDate effectiveDate = change.get("effectiveDate") == null ? null
                    : parseDate("change.effectiveDate", change.get("effectiveDate"), null);
            if (change.get("effectiveDate") == null) {
                errors.put("change.effectiveDate", "필수 항목입니다");
            }
            String reason = change.get("reason") instanceof String text ? text.trim() : "";
            if (reason.isEmpty() || reason.length() > 255) {
                errors.put("change.reason", reason.isEmpty() ? "필수 항목입니다" : "255자 이하로 입력하세요");
            }
            Object documentId = change.get("documentId");
            if (documentId != null && !(documentId instanceof Integer || documentId instanceof Long)) {
                errors.put("change.documentId", "다시 선택하세요");
                documentId = null;
            }
            return new Change(effectiveDate, reason, documentId == null ? null : ((Number) documentId).longValue());
        }

        void throwIfInvalid() {
            if (!errors.isEmpty()) {
                throw BusinessException.invalidFields(errors);
            }
        }

        private LocalDate parseDate(String field, Object value, LocalDate fallback) {
            try {
                return LocalDate.parse(String.valueOf(value));
            } catch (DateTimeParseException e) {
                errors.put(field, "날짜 형식(2026-10-01)으로 입력하세요");
                return fallback;
            }
        }
    }
}
