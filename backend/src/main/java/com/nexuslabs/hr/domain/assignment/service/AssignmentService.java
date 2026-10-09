package com.nexuslabs.hr.domain.assignment.service;

import com.nexuslabs.hr.domain.assignment.dto.AssignmentCorrectionRequest;
import com.nexuslabs.hr.domain.assignment.dto.AssignmentItem;
import com.nexuslabs.hr.domain.assignment.dto.AssignmentRequest;
import com.nexuslabs.hr.domain.assignment.entity.AssignmentHistory;
import com.nexuslabs.hr.domain.assignment.repository.AssignmentHistoryRepository;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.domain.org.entity.JobGrade;
import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.domain.org.entity.OrgUnit;
import com.nexuslabs.hr.domain.org.service.JobGradeService;
import com.nexuslabs.hr.domain.org.service.JobTitleService;
import com.nexuslabs.hr.domain.org.service.OrgUnitService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 인사발령 등록 · 정정(F-ASSIGN-01 · 04). 이력은 append-only(BR-ASSIGN-001)이고 발효일은 등록일(BR-ASSIGN-004).
 * 직원 행을 먼저 잠가 같은 직원의 발령 · 정정 · 퇴직을 한 줄로 세운다. 감사 로그 없음(BR-AUDIT-001 목록 밖).
 */
@Service
public class AssignmentService {

    private final AssignmentHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;
    private final OrgUnitService orgUnitService;
    private final JobGradeService jobGradeService;
    private final JobTitleService jobTitleService;
    private final AssignmentQueryService queryService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AssignmentService(AssignmentHistoryRepository historyRepository, EmployeeRepository employeeRepository,
                             OrgUnitService orgUnitService, JobGradeService jobGradeService,
                             JobTitleService jobTitleService, AssignmentQueryService queryService, JdbcTemplate jdbc,
                             Clock clock) {
        this.historyRepository = historyRepository;
        this.employeeRepository = employeeRepository;
        this.orgUnitService = orgUnitService;
        this.jobGradeService = jobGradeService;
        this.jobTitleService = jobTitleService;
        this.queryService = queryService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * 발령 등록: 변경 전 값은 서버가 채움 → 이력 추가 → 직원 현재 값 갱신 → 떠난 조직의 조직장 해제(BR-ORG-003).
     * 재직중이 아니면 EMPLOYEE_NOT_ACTIVE, 바뀌는 값이 없으면 BUSINESS_RULE_VIOLATION.
     */
    @Transactional
    public AssignmentItem register(LoginUser user, AssignmentRequest request) {
        long cid = user.companyId();
        Employee employee = lockEmployee(cid, request.employeeId());
        if (employee.getStatus() != EmpStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_ACTIVE);
        }
        Target to = resolve(new Target(employee.getOrgUnit(), employee.getJobGrade(), employee.getJobTitle()),
                request.toOrgUnitId(), request.toJobGradeId(), request.toJobTitleId());
        if (to.sameAs(employee.getOrgUnit(), employee.getJobGrade(), employee.getJobTitle())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "바뀌는 값이 없습니다");
        }

        AssignmentHistory history = historyRepository.save(new AssignmentHistory(employee, request.assignmentType(),
                employee.getOrgUnit(), to.orgUnit(), employee.getJobGrade(), to.jobGrade(), employee.getJobTitle(),
                to.jobTitle(), request.reason().strip(), LocalDate.now(clock), null, user.employeeId()));
        employee.changeAssignment(to.orgUnit(), to.jobGrade(), to.jobTitle());
        // 조직장 해제와 응답은 SQL 로 직원 행을 읽는다 — JPA 변경을 먼저 DB 에 내보낸다(백엔드 안내 6.1)
        employeeRepository.flush();
        orgUnitService.releaseLeadsIfLeft(cid, employee.getId());
        return queryService.item(cid, history.getId());
    }

    /**
     * 정정 발령: 원본과 같은 유형 · 같은 변경 전 값 · 같은 발효일의 새 행이 원본을 가리킨다(BR-ASSIGN-002).
     * 이미 정정된 원본은 INVALID_STATE(가장 최근 정정본을 정정한다), 원본과 같은 값이면 BUSINESS_RULE_VIOLATION,
     * 퇴직자 EMPLOYEE_RESIGNED. 직원 현재 값은 가장 최근 유효 행 기준으로 다시 계산한다.
     */
    @Transactional
    public AssignmentItem correct(LoginUser user, long assignmentId, AssignmentCorrectionRequest request) {
        long cid = user.companyId();
        Long employeeId = jdbc.queryForList(
                        "SELECT employee_id FROM assignment_history WHERE id = ? AND company_id = ?",
                        Long.class, assignmentId, cid).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Employee employee = lockEmployee(cid, employeeId);
        if (employee.getStatus() == EmpStatus.RESIGNED) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM assignment_history WHERE correction_of_id = ? AND company_id = ?)",
                Boolean.class, assignmentId, cid))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 정정된 발령입니다. 가장 최근 정정본을 정정하세요");
        }
        AssignmentHistory original = historyRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Target to = resolve(new Target(original.getToOrgUnit(), original.getToJobGrade(), original.getToJobTitle()),
                request.toOrgUnitId(), request.toJobGradeId(), request.toJobTitleId());
        if (to.sameAs(original.getToOrgUnit(), original.getToJobGrade(), original.getToJobTitle())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "정정할 값이 원본과 같습니다");
        }

        AssignmentHistory correction = historyRepository.save(new AssignmentHistory(employee,
                original.getAssignmentType(), original.getFromOrgUnit(), to.orgUnit(), original.getFromJobGrade(),
                to.jobGrade(), original.getFromJobTitle(), to.jobTitle(), request.reason().strip(),
                original.getEffectiveDate(), original, user.employeeId()));
        historyRepository.flush();
        if (latestValid(cid, employee.getId()) == correction.getId()) {
            employee.changeAssignment(to.orgUnit(), to.jobGrade(), to.jobTitle());
            employeeRepository.flush();
            orgUnitService.releaseLeadsIfLeft(cid, employee.getId());
        }
        return queryService.item(cid, correction.getId());
    }

    // ------------------------------------------------------------------

    private Employee lockEmployee(long cid, long employeeId) {
        if (jdbc.queryForList("SELECT id FROM employee WHERE id = ? AND company_id = ? FOR UPDATE",
                Long.class, employeeId, cid).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return employeeRepository.findById(employeeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 변경 후 값. 기준 값(base)과 같은 ID 는 그대로 쓰고, 새로 고른 값만 활성인지 본다(INACTIVE_REFERENCE). */
    private Target resolve(Target base, long orgUnitId, long jobGradeId, Long jobTitleId) {
        OrgUnit orgUnit = base.orgUnit() != null && base.orgUnit().getId() == orgUnitId
                ? base.orgUnit() : orgUnitService.requireActive(orgUnitId);
        JobGrade jobGrade = base.jobGrade() != null && base.jobGrade().getId() == jobGradeId
                ? base.jobGrade() : jobGradeService.requireActive(jobGradeId);
        JobTitle jobTitle = jobTitleId == null ? null
                : base.jobTitle() != null && base.jobTitle().getId().equals(jobTitleId)
                ? base.jobTitle() : jobTitleService.requireActive(jobTitleId);
        return new Target(orgUnit, jobGrade, jobTitle);
    }

    /**
     * 가장 최근 유효 행(아직 정정되지 않은 행). 정정본은 원본 자리에 선다 — 순서는 발효일, 같은 날이면 맨 처음 원본의 ID.
     * 그래서 앞선 발령을 정정해도 그 뒤 발령이 있으면 현재 값은 바뀌지 않는다.
     */
    private long latestValid(long cid, long employeeId) {
        Map<Long, Long> correctionOf = new HashMap<>();
        Map<Long, LocalDate> effectiveDate = new HashMap<>();
        jdbc.query("""
                        SELECT id, correction_of_id, effective_date FROM assignment_history
                        WHERE company_id = ? AND employee_id = ?
                        """,
                rs -> {
                    long id = rs.getLong("id");
                    effectiveDate.put(id, rs.getObject("effective_date", LocalDate.class));
                    Long of = rs.getObject("correction_of_id", Long.class);
                    if (of != null) {
                        correctionOf.put(id, of);
                    }
                },
                cid, employeeId);
        Set<Long> corrected = new HashSet<>(correctionOf.values());
        List<Long> valid = effectiveDate.keySet().stream().filter(id -> !corrected.contains(id)).toList();
        return valid.stream()
                .max(Comparator.<Long, LocalDate>comparing(effectiveDate::get).thenComparing(id -> root(correctionOf, id)))
                .orElseThrow();
    }

    private static long root(Map<Long, Long> correctionOf, long id) {
        while (correctionOf.containsKey(id)) {
            id = correctionOf.get(id);
        }
        return id;
    }

    private record Target(OrgUnit orgUnit, JobGrade jobGrade, JobTitle jobTitle) {
        boolean sameAs(OrgUnit o, JobGrade g, JobTitle t) {
            return Objects.equals(id(orgUnit), id(o)) && Objects.equals(id(jobGrade), id(g))
                    && Objects.equals(jobTitle == null ? null : jobTitle.getId(), t == null ? null : t.getId());
        }

        private static Long id(OrgUnit o) {
            return o == null ? null : o.getId();
        }

        private static Long id(JobGrade g) {
            return g == null ? null : g.getId();
        }
    }
}
