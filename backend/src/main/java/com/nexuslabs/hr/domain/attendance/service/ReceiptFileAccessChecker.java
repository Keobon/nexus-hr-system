package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.file.FileAccessChecker;
import com.nexuslabs.hr.global.permission.PermissionCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 경비 영수증 내려받기 권한(API 설계서 14장, BR-FILE-001) — 청구자 · 그 건의 승인자 · ATTENDANCE_READ 범위 안 · PAYROLL_READ.
 * 경비 줄이 참조하지 않는 파일이면 판단하지 않는다(empty).
 */
@Component
class ReceiptFileAccessChecker implements FileAccessChecker {

    private final JdbcTemplate jdbc;
    private final ApprovalService approvalService;
    private final RequestAccess access;

    ReceiptFileAccessChecker(JdbcTemplate jdbc, ApprovalService approvalService, RequestAccess access) {
        this.jdbc = jdbc;
        this.approvalService = approvalService;
        this.access = access;
    }

    @Override
    public Optional<Boolean> canRead(LoginUser user, long fileId) {
        List<long[]> claims = jdbc.query("""
                        SELECT DISTINCT c.id, c.employee_id FROM expense_claim_line l
                        JOIN expense_claim c ON c.id = l.expense_claim_id AND c.company_id = l.company_id
                        WHERE l.company_id = ? AND l.receipt_file_id = ?
                        """,
                (rs, i) -> new long[]{rs.getLong("id"), rs.getLong("employee_id")}, user.companyId(), fileId);
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(claims.stream().anyMatch(c -> access.canView(user, c[1],
                approvalService.steps(user.companyId(), ApprovalWorkType.TRIP_EXPENSE, c[0], user.employeeId()),
                PermissionCode.PAYROLL_READ)));
    }
}
