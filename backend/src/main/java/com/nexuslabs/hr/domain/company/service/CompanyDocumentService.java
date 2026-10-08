package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.company.dto.CompanyDocumentGroup;
import com.nexuslabs.hr.domain.company.dto.CompanyDocumentRequest;
import com.nexuslabs.hr.domain.company.entity.CompanyDocType;
import com.nexuslabs.hr.domain.company.entity.CompanyDocument;
import com.nexuslabs.hr.domain.company.repository.CompanyDocumentRepository;
import com.nexuslabs.hr.domain.employee.dto.DocumentVersion;
import com.nexuslabs.hr.domain.employee.service.DocumentFiles;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 회사 서류(F-COMP-07, BR-FILE-001). 같은 종류(기타는 같은 이름)를 새로 올리면 새 버전이 현재본이 되고 이전 파일은 이전 버전으로 남는다.
 * 삭제는 없다. 등록은 감사 로그(BR-AUDIT-001). 회사 정보 변경 이력(F-COMP-02)의 근거 서류로 고를 수 있다.
 */
@Service
public class CompanyDocumentService {

    private final CompanyDocumentRepository repository;
    private final DocumentFiles documentFiles;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public CompanyDocumentService(CompanyDocumentRepository repository, DocumentFiles documentFiles,
                                  JdbcTemplate jdbc, AuditLogger auditLogger, Clock clock) {
        this.repository = repository;
        this.documentFiles = documentFiles;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** 종류 순(기타는 이름 순 — DB 정렬 설정과 관계없이 글자 코드 순, 한글은 가나다) — 현재본과 이전 버전(최신순). */
    @Transactional(readOnly = true)
    public List<CompanyDocumentGroup> list(long companyId) {
        LocalDate today = LocalDate.now(clock);
        record Row(CompanyDocType docType, String docName, boolean current, DocumentVersion version) {
        }
        List<Row> rows = jdbc.query("SELECT " + DocumentFiles.VERSION_COLUMNS + """

                         FROM company_document d
                         JOIN file f ON f.id = d.file_id AND f.company_id = d.company_id
                         JOIN employee u ON u.id = d.uploaded_by AND u.company_id = d.company_id
                        WHERE d.company_id = ?
                        ORDER BY d.doc_type, COALESCE(d.doc_name, '') COLLATE "C", d.version_no DESC
                        """,
                (rs, i) -> new Row(CompanyDocType.valueOf(rs.getString("doc_type")), rs.getString("doc_name"),
                        rs.getBoolean("is_current"), DocumentFiles.version(rs, today)),
                companyId);
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        rows.forEach(r -> groups.computeIfAbsent(r.docType() + "/" + Objects.toString(r.docName(), ""),
                k -> new ArrayList<>()).add(r));
        return groups.values().stream().map(g -> new CompanyDocumentGroup(g.getFirst().docType(),
                        g.getFirst().docName(),
                        g.stream().filter(Row::current).map(Row::version).findFirst().orElse(null),
                        g.stream().filter(r -> !r.current()).map(Row::version).toList()))
                .toList();
    }

    /** 새 버전 등록. 같은 회사의 동시 등록은 회사 행 잠금으로 순서를 정한다. 응답 = 그 종류의 현재본과 이전 버전. */
    @Transactional
    public CompanyDocumentGroup create(LoginUser user, CompanyDocumentRequest request) {
        long cid = user.companyId();
        jdbc.query("SELECT id FROM company WHERE id = ? FOR UPDATE", (rs, i) -> rs.getLong(1), cid);
        String docName = DocumentFiles.docName(request.docType() == CompanyDocType.OTHER, request.docName());
        DocumentFiles.checkDates(request.issuedDate(), request.expiresAt());
        documentFiles.requireLinkable(user, request.fileId());

        List<CompanyDocument> same = repository.findByDocType(request.docType()).stream()
                .filter(d -> Objects.equals(d.getDocName(), docName))
                .toList();
        int next = same.stream().mapToInt(CompanyDocument::getVersionNo).max().orElse(0) + 1;
        same.stream().filter(CompanyDocument::isCurrent).forEach(CompanyDocument::retire);
        repository.flush();                                     // 현재본 내리기가 새 행 INSERT 보다 먼저(부분 UNIQUE)
        CompanyDocument saved = repository.saveAndFlush(new CompanyDocument(request.docType(), docName,
                request.fileId(), request.issuedDate(), request.expiresAt(), next, DocumentFiles.memo(request.memo()),
                user.employeeId()));

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("docType", request.docType().name());
        after.put("docName", docName);
        after.put("versionNo", next);
        after.put("fileId", request.fileId());
        after.put("issuedDate", request.issuedDate() == null ? null : request.issuedDate().toString());
        after.put("expiresAt", request.expiresAt() == null ? null : request.expiresAt().toString());
        auditLogger.log(user, AuditAction.CREATE, "COMPANY_DOCUMENT", saved.getId(), null, after);
        return list(cid).stream()
                .filter(g -> g.docType() == request.docType() && Objects.equals(g.docName(), docName))
                .findFirst().orElseThrow();
    }
}
