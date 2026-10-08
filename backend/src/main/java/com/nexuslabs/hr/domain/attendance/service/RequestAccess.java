package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.Scope;
import com.nexuslabs.hr.global.permission.ScopeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 연장근무 · 출장 · 경비 청구가 같이 쓰는 확인(API 설계서 7.2–7.3) — 신청자가 재직중인지, 상세를 볼 수 있는지.
 * 상세는 신청자 · 그 건의 승인자 · ATTENDANCE_READ 범위 안(경비는 PAYROLL_READ 도)만 본다.
 */
@Component
class RequestAccess {

    private final JdbcTemplate jdbc;
    private final ScopeResolver scopeResolver;

    RequestAccess(JdbcTemplate jdbc, ScopeResolver scopeResolver) {
        this.jdbc = jdbc;
        this.scopeResolver = scopeResolver;
    }

    /** 재직중만 신청한다(API 설계서 1.7). 퇴직자는 로그인 단계에서 막힌다. */
    void requireActive(long companyId, long employeeId) {
        Optional<String> status = jdbc.queryForList("SELECT status::text FROM employee WHERE id = ? AND company_id = ?",
                String.class, employeeId, companyId).stream().findFirst();
        if (!"ACTIVE".equals(status.orElse(null))) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_ACTIVE);
        }
    }

    /**
     * 신청자도 승인자도 아니면 조회 권한으로 판단한다. extraAllCode(경비의 PAYROLL_READ)가 있으면 전사로 본다.
     * 코드가 하나도 없으면 FORBIDDEN, ATTENDANCE_READ 범위 밖이면 OUT_OF_SCOPE.
     */
    void checkViewer(LoginUser user, long applicantId, List<ApprovalStepView> steps, PermissionCode... extraAllCodes) {
        if (applicantId == user.employeeId() || isApprover(user, steps)) {
            return;
        }
        for (PermissionCode code : extraAllCodes) {
            if (scopeResolver.findScope(user, code).isPresent()) {
                return;
            }
        }
        Scope scope = scopeResolver.findScope(user, PermissionCode.ATTENDANCE_READ)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        scope.assertContains(applicantId);
    }

    /** checkViewer 와 같은 기준의 예/아니오 — 영수증 내려받기 권한(FileAccessChecker)에 쓴다. */
    boolean canView(LoginUser user, long applicantId, List<ApprovalStepView> steps, PermissionCode... extraAllCodes) {
        if (applicantId == user.employeeId() || isApprover(user, steps)) {
            return true;
        }
        for (PermissionCode code : extraAllCodes) {
            if (scopeResolver.findScope(user, code).isPresent()) {
                return true;
            }
        }
        return scopeResolver.findScope(user, PermissionCode.ATTENDANCE_READ)
                .map(scope -> scope.contains(applicantId)).orElse(false);
    }

    static boolean isApprover(LoginUser user, Collection<ApprovalStepView> steps) {
        return steps.stream().anyMatch(s -> s.approverId() != null && s.approverId() == user.employeeId());
    }

    static OffsetDateTime seoul(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }
}
