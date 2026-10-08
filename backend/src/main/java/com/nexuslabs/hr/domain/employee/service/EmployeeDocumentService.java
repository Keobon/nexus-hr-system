package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.dto.DocumentVersion;
import com.nexuslabs.hr.domain.employee.dto.EmployeeDocumentGroup;
import com.nexuslabs.hr.domain.employee.dto.EmployeeDocumentRequest;
import com.nexuslabs.hr.domain.employee.entity.EmployeeDocType;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.entity.EmployeeDocument;
import com.nexuslabs.hr.domain.employee.repository.EmployeeDocumentRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
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
 * 직원 서류(F-EMP-08, BR-FILE-001). 같은 종류(기타는 같은 이름)를 새로 올리면 새 버전이 현재본이 되고 이전 파일은 이전 버전으로 남는다.
 * 삭제는 없다. 등록은 EMPLOYEE_MANAGE, 본인은 자기 서류 조회만. 퇴직자는 조회만 된다. 감사 로그 대상이 아니다(BR-AUDIT-001 목록에 없음).
 */
@Service
public class EmployeeDocumentService {

    private final EmployeeDocumentRepository repository;
    private final DocumentFiles documentFiles;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public EmployeeDocumentService(EmployeeDocumentRepository repository, DocumentFiles documentFiles,
                                   JdbcTemplate jdbc, EntityManager em, Clock clock) {
        this.repository = repository;
        this.documentFiles = documentFiles;
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    /** 종류 순(기타는 이름 순 — DB 정렬 설정과 관계없이 글자 코드 순, 한글은 가나다) — 현재본과 이전 버전(최신순). 다른 회사 직원은 404. */
    @Transactional(readOnly = true)
    public List<EmployeeDocumentGroup> list(long companyId, long employeeId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee WHERE id = ? AND company_id = ?)",
                Boolean.class, employeeId, companyId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        LocalDate today = LocalDate.now(clock);
        record Row(EmployeeDocType docType, String docName, boolean current, DocumentVersion version) {
        }
        List<Row> rows = jdbc.query("SELECT " + DocumentFiles.VERSION_COLUMNS + """

                         FROM employee_document d
                         JOIN file f ON f.id = d.file_id AND f.company_id = d.company_id
                         JOIN employee u ON u.id = d.uploaded_by AND u.company_id = d.company_id
                        WHERE d.company_id = ? AND d.employee_id = ?
                        ORDER BY d.doc_type, COALESCE(d.doc_name, '') COLLATE "C", d.version_no DESC
                        """,
                (rs, i) -> new Row(EmployeeDocType.valueOf(rs.getString("doc_type")), rs.getString("doc_name"),
                        rs.getBoolean("is_current"), DocumentFiles.version(rs, today)),
                companyId, employeeId);
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        rows.forEach(r -> groups.computeIfAbsent(r.docType() + "/" + Objects.toString(r.docName(), ""),
                k -> new ArrayList<>()).add(r));
        return groups.values().stream().map(g -> new EmployeeDocumentGroup(g.getFirst().docType(),
                        g.getFirst().docName(),
                        g.stream().filter(Row::current).map(Row::version).findFirst().orElse(null),
                        g.stream().filter(r -> !r.current()).map(Row::version).toList()))
                .toList();
    }

    /** 새 버전 등록. 같은 직원의 동시 등록은 직원 행 잠금으로 순서를 정한다. 응답 = 그 종류의 현재본과 이전 버전. */
    @Transactional
    public EmployeeDocumentGroup create(LoginUser user, long employeeId, EmployeeDocumentRequest request) {
        long cid = user.companyId();
        String status = jdbc.query("SELECT status::text FROM employee WHERE id = ? AND company_id = ? FOR UPDATE",
                        (rs, i) -> rs.getString(1), employeeId, cid)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if ("RESIGNED".equals(status)) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        String docName = DocumentFiles.docName(request.docType() == EmployeeDocType.OTHER, request.docName());
        DocumentFiles.checkDates(request.issuedDate(), request.expiresAt());
        documentFiles.requireLinkable(user, request.fileId());

        List<EmployeeDocument> same = repository.findByEmployee_IdAndDocType(employeeId, request.docType()).stream()
                .filter(d -> Objects.equals(d.getDocName(), docName))
                .toList();
        int next = same.stream().mapToInt(EmployeeDocument::getVersionNo).max().orElse(0) + 1;
        same.stream().filter(EmployeeDocument::isCurrent).forEach(EmployeeDocument::retire);
        repository.flush();                                     // 현재본 내리기가 새 행 INSERT 보다 먼저(부분 UNIQUE)
        repository.saveAndFlush(new EmployeeDocument(em.getReference(Employee.class, employeeId), request.docType(),
                docName, request.fileId(), request.issuedDate(), request.expiresAt(), next,
                DocumentFiles.memo(request.memo()), user.employeeId()));
        return list(cid, employeeId).stream()
                .filter(g -> g.docType() == request.docType() && Objects.equals(g.docName(), docName))
                .findFirst().orElseThrow();
    }
}
