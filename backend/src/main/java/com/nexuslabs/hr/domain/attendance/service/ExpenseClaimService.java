package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.domain.attendance.dto.ExpenseClaimCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.ExpenseClaimResponse;
import com.nexuslabs.hr.domain.attendance.dto.ExpenseClaimRow;
import com.nexuslabs.hr.domain.attendance.entity.BusinessTrip;
import com.nexuslabs.hr.domain.attendance.entity.ExpenseClaim;
import com.nexuslabs.hr.domain.attendance.entity.ExpenseClaimLine;
import com.nexuslabs.hr.domain.attendance.entity.ExpenseType;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.repository.BusinessTripRepository;
import com.nexuslabs.hr.domain.attendance.repository.ExpenseClaimRepository;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.domain.employee.service.DocumentFiles;
import com.nexuslabs.hr.global.file.FileService;
import com.nexuslabs.hr.global.file.StoredFile;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 출장 경비 청구(F-ATT-08, BR-ATT-006). 승인은 승인 엔진이 하고, 확정(정산 대기) · 반려는 {@link ExpenseClaimApprovalTarget}이
 * 한다. 합계는 저장하지 않고 줄 금액을 더한다. 등록·수정은 JPA, 조회는 JDBC.
 */
@Service
public class ExpenseClaimService {

    private final ExpenseClaimRepository repository;
    private final BusinessTripRepository tripRepository;
    private final ExpenseTypeService expenseTypeService;
    private final FileService fileService;
    private final DocumentFiles documentFiles;
    private final ApprovalService approvalService;
    private final ScopeResolver scopeResolver;
    private final RequestAccess access;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public ExpenseClaimService(ExpenseClaimRepository repository, BusinessTripRepository tripRepository,
                               ExpenseTypeService expenseTypeService, FileService fileService,
                               DocumentFiles documentFiles,
                               ApprovalService approvalService, ScopeResolver scopeResolver, RequestAccess access,
                               JdbcTemplate jdbc, EntityManager em, Clock clock) {
        this.repository = repository;
        this.tripRepository = tripRepository;
        this.expenseTypeService = expenseTypeService;
        this.fileService = fileService;
        this.documentFiles = documentFiles;
        this.approvalService = approvalService;
        this.scopeResolver = scopeResolver;
        this.access = access;
        this.jdbc = jdbc;
        this.em = em;
        this.clock = clock;
    }

    /**
     * 청구 → 승인 단계 저장. 내 승인완료 출장이고 시작일 당일부터 청구할 수 있다(아니면 INVALID_STATE).
     * 출장마다 진행 중(승인대기·승인완료) 청구는 1건(EXPENSE_CLAIM_DUPLICATE), 반려·철회됐으면 새로 청구한다.
     * 줄 검사: 활성 경비 종류, 사용일은 출장 기간 안, 영수증은 본인이 올린 파일 · 줄마다 다른 파일,
     * 영수증 필수 종류에 파일 없음 → EXPENSE_RECEIPT_REQUIRED(details.lineIndex).
     */
    @Transactional
    public ExpenseClaimResponse create(LoginUser user, ExpenseClaimCreateRequest request) {
        long cid = user.companyId();
        access.requireActive(cid, user.employeeId());
        BusinessTrip trip = tripRepository.findById(request.businessTripId())
                .filter(t -> t.getEmployee().getId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (trip.getStatus() != RequestStatus.APPROVED || LocalDate.now(clock).isBefore(trip.getStartDate())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "승인된 출장의 시작일부터 경비를 청구할 수 있습니다");
        }
        if (hasActiveClaim(cid, trip.getId())) {
            throw new BusinessException(ErrorCode.EXPENSE_CLAIM_DUPLICATE);
        }

        List<ExpenseType> types = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        Set<Long> usedFiles = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            ExpenseClaimCreateRequest.Line line = request.lines().get(i);
            types.add(expenseTypeService.requireActive(line.expenseTypeId()));
            if (line.usedDate().isBefore(trip.getStartDate()) || line.usedDate().isAfter(trip.getEndDate())) {
                errors.put("lines[%d].usedDate".formatted(i), "사용일은 출장 기간 안이어야 합니다");
            }
            if (line.receiptFileId() != null) {
                Optional<StoredFile> file = fileService.find(cid, line.receiptFileId());
                if (file.isEmpty() || file.get().uploadedBy() == null || file.get().uploadedBy() != user.employeeId()) {
                    errors.put("lines[%d].receiptFileId".formatted(i), "본인이 올린 영수증 파일만 붙일 수 있습니다");
                } else if (!usedFiles.add(line.receiptFileId())) {
                    errors.put("lines[%d].receiptFileId".formatted(i), "같은 파일을 두 줄에 붙일 수 없습니다");
                } else if (documentFiles.linked(cid, line.receiptFileId(), false)) {
                    // 다른 청구 · 서류 · 프로필 사진 · 로고에 쓰인 파일(반려 · 취소된 청구의 영수증은 다시 쓸 수 있다)
                    errors.put("lines[%d].receiptFileId".formatted(i), "이미 다른 곳에 쓰인 파일입니다");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).isReceiptRequired() && request.lines().get(i).receiptFileId() == null) {
                throw new BusinessException(ErrorCode.EXPENSE_RECEIPT_REQUIRED,
                        Map.of("lineIndex", i, "expenseTypeName", types.get(i).getName()));
            }
        }

        ExpenseClaim claim = new ExpenseClaim(trip, em.getReference(Employee.class, user.employeeId()));
        for (int i = 0; i < types.size(); i++) {
            ExpenseClaimCreateRequest.Line line = request.lines().get(i);
            claim.addLine(new ExpenseClaimLine(claim, types.get(i), line.usedDate(), line.amount(),
                    blankToNull(line.description()), line.receiptFileId()));
        }
        // 동시에 두 번 보내면 부분 유일 인덱스(ux_expense_claim_active)가 막고 EXPENSE_CLAIM_DUPLICATE 로 응답한다
        repository.saveAndFlush(claim);
        approvalService.open(cid, ApprovalWorkType.TRIP_EXPENSE, claim.getId(), user.employeeId());
        repository.flush();
        return detail(cid, claim.getId(), user.employeeId());
    }

    /** 신청자 · 그 건의 승인자 · ATTENDANCE_READ(범위 안) · PAYROLL_READ 만 본다. */
    @Transactional(readOnly = true)
    public ExpenseClaimResponse get(LoginUser user, long id) {
        ExpenseClaimResponse claim = detail(user.companyId(), id, user.employeeId());
        access.checkViewer(user, claim.employeeId(), claim.approvalSteps(), PermissionCode.PAYROLL_READ);
        return claim;
    }

    /** 승인대기 중에만 본인이 철회한다. 남의 청구는 404. */
    @Transactional
    public ExpenseClaimResponse withdraw(LoginUser user, long id) {
        ExpenseClaim claim = repository.findById(id)
                .filter(c -> c.getEmployee().getId() == user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // 잠금 순서는 승인 처리와 같게 — 승인 단계 먼저, 청구 행 다음(교착 방지). 진행 중인 최종 승인이 있으면
        // 그 커밋을 기다린 뒤 최신 상태로 다시 판단한다
        approvalService.withdraw(user.companyId(), ApprovalWorkType.TRIP_EXPENSE, id);
        em.refresh(claim, LockModeType.PESSIMISTIC_WRITE);
        if (claim.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "승인대기 중인 청구만 철회할 수 있습니다");
        }
        claim.cancel(null);
        repository.flush();
        return detail(user.companyId(), id, user.employeeId());
    }

    /** 내 청구 목록. 최근 청구부터. */
    @Transactional(readOnly = true)
    public PageImpl<ExpenseClaimRow> mine(LoginUser user, RequestStatus status, Pageable pageable) {
        return page(user.companyId(), " AND c.employee_id = ?", List.of(user.employeeId()), status, null, null,
                pageable);
    }

    /**
     * 다른 직원의 청구 목록(ATTENDANCE_READ · PAYROLL_READ). PAYROLL_READ 가 있으면 전사(급여에는 팀 범위가 없다 — BR-PAY-001),
     * 아니면 ATTENDANCE_READ 범위. settled=false 는 정산 대기(승인완료 · 미반영), true 는 반영된 청구.
     */
    @Transactional(readOnly = true)
    public PageImpl<ExpenseClaimRow> list(LoginUser user, RequestStatus status, Boolean settled, Long orgUnitId,
                                          Pageable pageable) {
        Scope scope = scopeResolver.findScope(user, PermissionCode.PAYROLL_READ).isPresent() ? Scope.company()
                : scopeResolver.scopeOf(user, PermissionCode.ATTENDANCE_READ);
        if (!scope.all() && scope.employeeIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return scope.all()
                ? page(user.companyId(), "", List.of(), status, settled, orgUnitId, pageable)
                : page(user.companyId(), " AND c.employee_id = ANY(?)",
                        List.of((Object) scope.employeeIds().toArray(Long[]::new)), status, settled, orgUnitId, pageable);
    }

    /** 퇴직 처리(B-10)가 같은 트랜잭션에서 부른다(역할 분담 v2 2.1). 승인대기 청구를 취소(사유 "퇴직")하고 단계를 닫는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelPendingByResignation(long companyId, long employeeId) {
        List<Long> ids = jdbc.queryForList("""
                        SELECT id FROM expense_claim
                        WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'
                        FOR UPDATE
                        """,
                Long.class, companyId, employeeId);
        for (long id : ids) {
            jdbc.update("""
                            UPDATE expense_claim SET status = 'CANCELLED', cancel_reason = ?, updated_at = now()
                            WHERE id = ? AND company_id = ?
                            """,
                    OvertimeService.RESIGNATION_REASON, id, companyId);
            approvalService.withdraw(companyId, ApprovalWorkType.TRIP_EXPENSE, id);
        }
        return ids.size();
    }

    /** 홈 대시보드 me.myPending.expenseClaim(역할 분담 v2 2.1) — 그 직원이 청구한 승인대기 건수. */
    @Transactional(readOnly = true)
    public long countPending(long companyId, long employeeId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM expense_claim WHERE company_id = ? AND employee_id = ? AND status = 'PENDING'",
                Long.class, companyId, employeeId);
    }

    // ------------------------------------------------------------------

    private boolean hasActiveClaim(long companyId, long tripId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM expense_claim
                                       WHERE company_id = ? AND business_trip_id = ? AND status IN ('PENDING', 'APPROVED'))
                        """,
                Boolean.class, companyId, tripId));
    }

    private ExpenseClaimResponse detail(long companyId, long id, long viewerId) {
        List<ApprovalStepView> steps = approvalService.steps(companyId, ApprovalWorkType.TRIP_EXPENSE, id, viewerId);
        List<ExpenseClaimResponse.Line> lines = jdbc.query("""
                        SELECT l.id, l.expense_type_id, t.name AS type_name, l.used_date, l.amount, l.description,
                               f.id AS file_id, f.original_name, f.content_type, f.size_bytes
                        FROM expense_claim_line l
                        JOIN expense_type t ON t.id = l.expense_type_id AND t.company_id = l.company_id
                        LEFT JOIN file f ON f.id = l.receipt_file_id AND f.company_id = l.company_id
                        WHERE l.company_id = ? AND l.expense_claim_id = ?
                        ORDER BY l.id
                        """,
                (rs, i) -> new ExpenseClaimResponse.Line(rs.getLong("id"), rs.getLong("expense_type_id"),
                        rs.getString("type_name"), rs.getObject("used_date", LocalDate.class), rs.getLong("amount"),
                        rs.getString("description"),
                        rs.getObject("file_id") == null ? null : new StoredFile.FileInfo(rs.getLong("file_id"),
                                rs.getString("original_name"), rs.getString("content_type"), rs.getLong("size_bytes"))),
                companyId, id);
        return jdbc.query(SELECT + FROM + " WHERE c.id = ? AND c.company_id = ?",
                        (rs, i) -> new ExpenseClaimResponse(rs.getLong("id"), rs.getLong("business_trip_id"),
                                rs.getString("destination"), rs.getObject("start_date", LocalDate.class),
                                rs.getObject("end_date", LocalDate.class), rs.getLong("employee_id"),
                                rs.getString("employee_name"), rs.getString("org_unit_name"),
                                RequestStatus.valueOf(rs.getString("status")), rs.getString("cancel_reason"),
                                rs.getLong("total_amount"), rs.getString("pay_month"),
                                RequestAccess.seoul(rs.getObject("created_at", OffsetDateTime.class)), lines, steps),
                        id, companyId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private PageImpl<ExpenseClaimRow> page(long companyId, String employeeFilter, List<Object> employeeArgs,
                                           RequestStatus status, Boolean settled, Long orgUnitId, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE c.company_id = ?").append(employeeFilter);
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.addAll(employeeArgs);
        if (status != null) {
            where.append(" AND c.status = ?::request_status");
            args.add(status.name());
        }
        if (Boolean.TRUE.equals(settled)) {
            where.append(" AND c.paystub_id IS NOT NULL");
        } else if (Boolean.FALSE.equals(settled)) {
            where.append(" AND c.status = 'APPROVED' AND c.paystub_id IS NULL");
        }
        if (orgUnitId != null) {
            where.append("""
                     AND e.org_unit_id IN (WITH RECURSIVE sub AS (
                         SELECT id FROM org_unit WHERE id = ? AND company_id = ?
                         UNION
                         SELECT o.id FROM org_unit o JOIN sub s ON o.parent_id = s.id WHERE o.company_id = ?)
                       SELECT id FROM sub)
                    """);
            args.addAll(List.of(orgUnitId, companyId, companyId));
        }
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());
        args.addAll(List.of(pageable.getPageSize(), pageable.getOffset()));
        List<ExpenseClaimRow> rows = jdbc.query(SELECT + FROM + where
                        + " ORDER BY c.created_at DESC, c.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new ExpenseClaimRow(rs.getLong("id"), rs.getLong("business_trip_id"),
                        rs.getString("destination"), rs.getLong("employee_id"), rs.getString("employee_name"),
                        rs.getString("org_unit_name"), rs.getLong("total_amount"), rs.getInt("line_count"),
                        RequestStatus.valueOf(rs.getString("status")), rs.getString("pay_month"),
                        RequestAccess.seoul(rs.getObject("created_at", OffsetDateTime.class)), null),
                args.toArray());
        Map<Long, CurrentStep> steps = approvalService.currentSteps(companyId, ApprovalWorkType.TRIP_EXPENSE,
                rows.stream().map(ExpenseClaimRow::id).toList());
        return new PageImpl<>(rows.stream().map(r -> r.withCurrentStep(steps.get(r.id()))).toList(), pageable, total);
    }

    private static final String SELECT = """
            SELECT c.id, c.business_trip_id, t.destination, t.start_date, t.end_date, c.employee_id,
                   e.name AS employee_name, u.name AS org_unit_name, c.status::text AS status, c.cancel_reason,
                   c.created_at, r.pay_month, s.total_amount, s.line_count
            """;

    private static final String FROM = """
             FROM expense_claim c
             JOIN business_trip t ON t.id = c.business_trip_id AND t.company_id = c.company_id
             JOIN employee e ON e.id = c.employee_id AND e.company_id = c.company_id
             JOIN org_unit u ON u.id = e.org_unit_id AND u.company_id = e.company_id
             LEFT JOIN paystub p ON p.id = c.paystub_id AND p.company_id = c.company_id
             LEFT JOIN payroll_run r ON r.id = p.payroll_run_id AND r.company_id = c.company_id
             CROSS JOIN LATERAL (
                 SELECT COALESCE(sum(l.amount), 0) AS total_amount, count(*) AS line_count
                 FROM expense_claim_line l WHERE l.company_id = c.company_id AND l.expense_claim_id = c.id
             ) s
            """;

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
