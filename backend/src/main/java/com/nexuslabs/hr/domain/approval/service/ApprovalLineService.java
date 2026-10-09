package com.nexuslabs.hr.domain.approval.service;

import com.nexuslabs.hr.domain.approval.dto.ApprovalLineRequest;
import com.nexuslabs.hr.domain.approval.dto.ApprovalLineResponse;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.response.DeleteResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 승인선 관리(F-APPR-01, BR-APPR-001). 바꿔도 진행 중인 건에는 영향이 없다 — 승인자는 신청 때 저장된다.
 * 업무 종류마다 기본 승인선(조건 없음)이 1개 있고 삭제·비활성화·조건 지정을 할 수 없다.
 */
@Service
public class ApprovalLineService {

    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;

    public ApprovalLineService(JdbcTemplate jdbc, AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
    }

    /** 우선순위 순, 기본 승인선은 맨 끝. workType 이 null 이면 전체. */
    @Transactional(readOnly = true)
    public List<ApprovalLineResponse> list(LoginUser user, ApprovalWorkType workType) {
        List<Long> ids = jdbc.queryForList("""
                        SELECT id FROM approval_line
                        WHERE company_id = ? AND (?::approval_work_type IS NULL OR work_type = ?::approval_work_type)
                        ORDER BY work_type, is_default, priority, id
                        """,
                Long.class, user.companyId(), name(workType), name(workType));
        return ids.stream().map(id -> get(user.companyId(), id)).toList();
    }

    @Transactional
    public ApprovalLineResponse create(LoginUser user, ApprovalLineRequest request) {
        validate(user.companyId(), request, null, false);
        long id = jdbc.queryForObject("""
                        INSERT INTO approval_line (company_id, name, work_type, cond_job_title_id, cond_role_id,
                                                   is_default, priority, is_active)
                        VALUES (?, ?, ?::approval_work_type, ?, ?, FALSE, ?, ?) RETURNING id
                        """,
                Long.class, user.companyId(), request.name().trim(), request.workType().name(),
                request.condJobTitleId(), request.condRoleId(), request.priority(), request.isActive());
        insertSteps(user.companyId(), id, request.steps());
        ApprovalLineResponse created = get(user.companyId(), id);
        auditLogger.log(user, AuditAction.CREATE, "APPROVAL_LINE", id, null, created);
        return created;
    }

    @Transactional
    public ApprovalLineResponse update(LoginUser user, long lineId, ApprovalLineRequest request) {
        ApprovalLineResponse before = get(user.companyId(), lineId);
        if (before.isDefault() && (request.condJobTitleId() != null || request.condRoleId() != null
                || !request.isActive() || request.workType() != before.workType())) {
            throw new BusinessException(ErrorCode.APPROVAL_DEFAULT_LOCKED,
                    "기본 승인선은 조건을 지정하거나 비활성화하거나 업무 종류를 바꿀 수 없습니다");
        }
        validate(user.companyId(), request, lineId, before.isDefault());
        jdbc.update("""
                        UPDATE approval_line SET name = ?, work_type = ?::approval_work_type, cond_job_title_id = ?,
                               cond_role_id = ?, priority = ?, is_active = ?, updated_at = now()
                        WHERE id = ? AND company_id = ?
                        """,
                request.name().trim(), request.workType().name(), request.condJobTitleId(), request.condRoleId(),
                request.priority(), request.isActive(), lineId, user.companyId());
        jdbc.update("DELETE FROM approval_line_step WHERE approval_line_id = ? AND company_id = ?",
                lineId, user.companyId());
        insertSteps(user.companyId(), lineId, request.steps());
        ApprovalLineResponse after = get(user.companyId(), lineId);
        auditLogger.log(user, AuditAction.UPDATE, "APPROVAL_LINE", lineId, before, after);
        return after;
    }

    /** 쓰인 적 있으면(진행·완료된 신청의 단계가 참조) 비활성화, 없으면 삭제. */
    @Transactional
    public DeleteResult delete(LoginUser user, long lineId) {
        ApprovalLineResponse before = get(user.companyId(), lineId);
        if (before.isDefault()) {
            throw new BusinessException(ErrorCode.APPROVAL_DEFAULT_LOCKED);
        }
        Boolean used = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM approval_step WHERE company_id = ? AND approval_line_id = ?)",
                Boolean.class, user.companyId(), lineId);
        DeleteResult result;
        if (Boolean.TRUE.equals(used)) {
            jdbc.update("UPDATE approval_line SET is_active = FALSE, updated_at = now() WHERE id = ? AND company_id = ?",
                    lineId, user.companyId());
            result = DeleteResult.deactivated();
        } else {
            jdbc.update("DELETE FROM approval_line WHERE id = ? AND company_id = ?", lineId, user.companyId());
            result = DeleteResult.deleted();
        }
        auditLogger.log(user, AuditAction.DELETE, "APPROVAL_LINE", lineId, before, Map.of("result", result.result()));
        return result;
    }

    private void validate(long companyId, ApprovalLineRequest r, Long exceptLineId, boolean isDefault) {
        if (r.workType() == ApprovalWorkType.LEAVE_CANCEL) {
            throw BusinessException.invalidFields(Map.of("workType", "휴가 취소는 승인선이 없습니다(원래 휴가의 마지막 승인자가 승인)"));
        }
        if (r.condJobTitleId() != null && r.condRoleId() != null) {
            throw BusinessException.invalidFields(Map.of("condRoleId", "적용 조건은 직책과 역할 중 하나만 고릅니다"));
        }
        if (!isDefault && r.condJobTitleId() == null && r.condRoleId() == null) {
            throw BusinessException.invalidFields(Map.of("condJobTitleId", "적용 조건(신청자의 직책 또는 역할)을 고르세요. 조건 없는 승인선은 기본 승인선뿐입니다"));
        }
        requireExists(companyId, "job_title", r.condJobTitleId(), "직책");
        requireExists(companyId, "role", r.condRoleId(), "역할");

        // 같은 업무 · 같은 조건의 활성 승인선은 하나만
        if (r.isActive() && !isDefault) {
            Boolean duplicate = jdbc.queryForObject("""
                            SELECT EXISTS (SELECT 1 FROM approval_line
                                           WHERE company_id = ? AND work_type = ?::approval_work_type AND is_active
                                             AND id <> COALESCE(?, -1)
                                             AND cond_job_title_id IS NOT DISTINCT FROM ?
                                             AND cond_role_id IS NOT DISTINCT FROM ?)
                            """,
                    Boolean.class, companyId, r.workType().name(), exceptLineId, r.condJobTitleId(), r.condRoleId());
            if (Boolean.TRUE.equals(duplicate)) {
                throw BusinessException.invalidFields(Map.of(r.condJobTitleId() != null ? "condJobTitleId" : "condRoleId",
                        "같은 조건의 활성 승인선이 이미 있습니다"));
            }
        }

        List<ApprovalLineRequest.Step> steps = r.steps().stream()
                .sorted(Comparator.comparing(ApprovalLineRequest.Step::stepOrder)).toList();
        for (int i = 0; i < steps.size(); i++) {
            ApprovalLineRequest.Step s = steps.get(i);
            if (s.stepOrder() != i + 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "단계 순서는 1부터 빈틈없이 이어져야 합니다");
            }
            switch (s.approverType()) {
                case ORG_LEAD -> { }
                case ORG_LEAD_UP -> {
                    if (s.upLevels() == null) {
                        throw stepValueMissing(s, "몇 단계 위인지(upLevels)");
                    }
                }
                case JOB_TITLE -> {
                    if (s.jobTitleId() == null) {
                        throw stepValueMissing(s, "직책(jobTitleId)");
                    }
                    requireExists(companyId, "job_title", s.jobTitleId(), "직책");
                }
                case EMPLOYEE -> {
                    if (s.employeeId() == null) {
                        throw stepValueMissing(s, "직원(employeeId)");
                    }
                    requireExists(companyId, "employee", s.employeeId(), "직원");
                }
            }
        }
    }

    private static BusinessException stepValueMissing(ApprovalLineRequest.Step s, String what) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, s.stepOrder() + "단계: " + what + "을(를) 지정하세요",
                Map.of("stepOrder", s.stepOrder()));
    }

    /** 다른 회사 ID면 404 — 존재 여부를 알려주지 않는다. table 은 코드 안의 상수만 넘긴다. */
    private void requireExists(long companyId, String table, Long id, String label) {
        if (id == null) {
            return;
        }
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ? AND company_id = ?)", Boolean.class, id, companyId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, label + "을(를) 찾을 수 없습니다");
        }
    }

    /** 승인자 지정 방식에 필요한 값만 저장한다(나머지는 비운다 — DB CHECK 와 같은 규칙). */
    private void insertSteps(long companyId, long lineId, List<ApprovalLineRequest.Step> steps) {
        for (ApprovalLineRequest.Step s : steps) {
            jdbc.update("""
                            INSERT INTO approval_line_step (company_id, approval_line_id, step_order, approver_type,
                                                            up_levels, job_title_id, employee_id)
                            VALUES (?, ?, ?, ?::approver_type, ?, ?, ?)
                            """,
                    companyId, lineId, s.stepOrder(), s.approverType().name(),
                    s.approverType() == ApproverType.ORG_LEAD_UP ? s.upLevels() : null,
                    s.approverType() == ApproverType.JOB_TITLE ? s.jobTitleId() : null,
                    s.approverType() == ApproverType.EMPLOYEE ? s.employeeId() : null);
        }
    }

    private ApprovalLineResponse get(long companyId, long lineId) {
        Map<String, Object> line = jdbc.queryForList("""
                        SELECT l.id, l.name, l.work_type::text AS work_type, l.cond_job_title_id, t.name AS cond_job_title_name,
                               l.cond_role_id, r.name AS cond_role_name, l.priority, l.is_default, l.is_active
                        FROM approval_line l
                        LEFT JOIN job_title t ON t.id = l.cond_job_title_id AND t.company_id = l.company_id
                        LEFT JOIN role r ON r.id = l.cond_role_id AND r.company_id = l.company_id
                        WHERE l.id = ? AND l.company_id = ?
                        """,
                lineId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<ApprovalLineResponse.Step> steps = jdbc.query("""
                        SELECT s.step_order, s.approver_type::text AS approver_type, s.up_levels, s.job_title_id,
                               t.name AS job_title_name, s.employee_id, e.name AS employee_name
                        FROM approval_line_step s
                        LEFT JOIN job_title t ON t.id = s.job_title_id AND t.company_id = s.company_id
                        LEFT JOIN employee e ON e.id = s.employee_id AND e.company_id = s.company_id
                        WHERE s.approval_line_id = ? AND s.company_id = ?
                        ORDER BY s.step_order
                        """,
                (rs, i) -> new ApprovalLineResponse.Step(rs.getInt("step_order"),
                        ApproverType.valueOf(rs.getString("approver_type")), rs.getObject("up_levels", Integer.class),
                        rs.getObject("job_title_id", Long.class), rs.getString("job_title_name"),
                        rs.getObject("employee_id", Long.class), rs.getString("employee_name")),
                lineId, companyId);
        Map<String, Object> l = new HashMap<>(line);
        return new ApprovalLineResponse(((Number) l.get("id")).longValue(), (String) l.get("name"),
                ApprovalWorkType.valueOf((String) l.get("work_type")), toLong(l.get("cond_job_title_id")),
                (String) l.get("cond_job_title_name"), toLong(l.get("cond_role_id")), (String) l.get("cond_role_name"),
                ((Number) l.get("priority")).intValue(), (Boolean) l.get("is_default"), (Boolean) l.get("is_active"),
                new ArrayList<>(steps));
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static String name(ApprovalWorkType workType) {
        return workType == null ? null : workType.name();
    }
}
