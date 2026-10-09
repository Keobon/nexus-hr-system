package com.nexuslabs.hr.domain.evaluation.service;

import com.nexuslabs.hr.domain.evaluation.dto.EvalCycleItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvalCycleRequest;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTargetPreview;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTargetRules;
import com.nexuslabs.hr.domain.evaluation.entity.EvalCycle;
import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleStatus;
import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleTarget;
import com.nexuslabs.hr.domain.evaluation.repository.EvalCycleRepository;
import com.nexuslabs.hr.domain.evaluation.repository.EvalCycleTargetRepository;
import com.nexuslabs.hr.domain.org.service.EmploymentTypeService;
import com.nexuslabs.hr.domain.org.service.JobGradeService;
import com.nexuslabs.hr.domain.org.service.JobTitleService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 평가 기간 · 대상 규칙 · 시작 · 마감(F-EVAL-01 · 03). 상태: 예정(SCHEDULED) → 진행(IN_PROGRESS) → 종료(CLOSED).
 * 시작 때 대상자마다 평가 1건을 만들고 대상자 · 평가자 · 템플릿을 정해 저장한다(이후 조직이 바뀌어도 그대로).
 * 감사 로그 없음(BR-AUDIT-001 목록 밖).
 */
@Service
public class EvalCycleService {

    private static final Set<String> PATCH_FIELDS = Set.of("name", "startDate", "endDate");

    private final EvalCycleRepository cycleRepository;
    private final EvalCycleTargetRepository targetRepository;
    private final EvalTemplateService templateService;
    private final JobTitleService jobTitleService;
    private final JobGradeService jobGradeService;
    private final EmploymentTypeService employmentTypeService;
    private final EvalTargetCalculator calculator;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EvalCycleService(EvalCycleRepository cycleRepository, EvalCycleTargetRepository targetRepository,
                            EvalTemplateService templateService, JobTitleService jobTitleService,
                            JobGradeService jobGradeService, EmploymentTypeService employmentTypeService,
                            EvalTargetCalculator calculator, JdbcTemplate jdbc, Clock clock) {
        this.cycleRepository = cycleRepository;
        this.targetRepository = targetRepository;
        this.templateService = templateService;
        this.jobTitleService = jobTitleService;
        this.jobGradeService = jobGradeService;
        this.employmentTypeService = employmentTypeService;
        this.calculator = calculator;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 최근 시작일부터. */
    @Transactional(readOnly = true)
    public List<EvalCycleItem> list(LoginUser user) {
        return jdbc.query(SELECT + " WHERE c.company_id = ? ORDER BY c.start_date DESC, c.id DESC",
                (rs, i) -> map(rs, null), user.companyId());
    }

    /** 시작일 > 종료일 → fields.endDate. 다른 기간과 겹치면 만들고 warning = PERIOD_OVERLAP. 201. */
    @Transactional
    public EvalCycleItem create(LoginUser user, EvalCycleRequest request) {
        checkDates(request.startDate(), request.endDate());
        EvalCycle cycle = cycleRepository.saveAndFlush(
                new EvalCycle(request.name().strip(), request.startDate(), request.endDate()));
        return item(user.companyId(), cycle.getId(), overlapWarning(user.companyId(), cycle));
    }

    /** 예정이면 셋 다, 진행 중이면 종료일 연장만(다른 항목 → INVALID_STATE, 앞당김 → fields.endDate), 종료면 INVALID_STATE. */
    @Transactional
    public EvalCycleItem update(LoginUser user, long id, Map<String, Object> patch) {
        PatchRequest.check(patch, PATCH_FIELDS, PATCH_FIELDS);
        EvalCycle cycle = lock(user.companyId(), id);
        if (cycle.getStatus() == EvalCycleStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "종료된 평가 기간은 바꿀 수 없습니다");
        }
        String name = patch.containsKey("name") ? parseName(patch.get("name")) : cycle.getName();
        LocalDate start = patch.containsKey("startDate") ? parseDate("startDate", patch.get("startDate"))
                : cycle.getStartDate();
        LocalDate end = patch.containsKey("endDate") ? parseDate("endDate", patch.get("endDate")) : cycle.getEndDate();
        if (cycle.getStatus() == EvalCycleStatus.IN_PROGRESS) {
            if (!name.equals(cycle.getName()) || !start.equals(cycle.getStartDate())) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "진행 중인 평가 기간은 종료일 연장만 할 수 있습니다");
            }
            if (end.isBefore(cycle.getEndDate())) {
                throw BusinessException.invalidFields(Map.of("endDate", "진행 중에는 종료일을 늦추기만 할 수 있습니다"));
            }
        }
        checkDates(start, end);
        cycle.update(name, start, end);
        cycleRepository.flush();
        return item(user.companyId(), id, overlapWarning(user.companyId(), cycle));
    }

    /** 우선순위 순. */
    @Transactional(readOnly = true)
    public EvalTargetRules rules(LoginUser user, long cycleId) {
        get(cycleId);
        return new EvalTargetRules(jdbc.query("""
                        SELECT r.priority, r.cond_is_org_lead, r.cond_job_title_id, jt.name AS job_title_name,
                               r.cond_job_grade_id, jg.name AS job_grade_name, r.cond_employment_type_id,
                               et.name AS employment_type_name, r.eval_template_id, t.name AS template_name
                        FROM eval_cycle_target r
                             LEFT JOIN job_title jt ON jt.id = r.cond_job_title_id AND jt.company_id = r.company_id
                             LEFT JOIN job_grade jg ON jg.id = r.cond_job_grade_id AND jg.company_id = r.company_id
                             LEFT JOIN employment_type et ON et.id = r.cond_employment_type_id AND et.company_id = r.company_id
                             LEFT JOIN eval_template t ON t.id = r.eval_template_id AND t.company_id = r.company_id
                        WHERE r.company_id = ? AND r.eval_cycle_id = ?
                        ORDER BY r.priority
                        """,
                (rs, i) -> new EvalTargetRules.Rule(rs.getInt("priority"), rs.getObject("cond_is_org_lead", Boolean.class),
                        rs.getObject("cond_job_title_id", Long.class), rs.getString("job_title_name"),
                        rs.getObject("cond_job_grade_id", Long.class), rs.getString("job_grade_name"),
                        rs.getObject("cond_employment_type_id", Long.class), rs.getString("employment_type_name"),
                        rs.getObject("eval_template_id", Long.class), rs.getString("template_name")),
                user.companyId(), cycleId));
    }

    /**
     * 통째로 교체 — 예정 상태만(아니면 INVALID_STATE). 우선순위가 겹치면 fields.rules. 고르는 직책 · 직급 · 고용형태 ·
     * 템플릿은 활성이어야 한다(INACTIVE_REFERENCE). 이름 필드는 무시한다.
     */
    @Transactional
    public EvalTargetRules replaceRules(LoginUser user, long cycleId, EvalTargetRules request) {
        EvalCycle cycle = lock(user.companyId(), cycleId);
        if (cycle.getStatus() != EvalCycleStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "대상 규칙은 평가 시작 전에만 바꿀 수 있습니다");
        }
        Set<Integer> priorities = new HashSet<>();
        for (EvalTargetRules.Rule r : request.rules()) {
            if (!priorities.add(r.priority())) {
                throw BusinessException.invalidFields(Map.of("rules", "우선순위 " + r.priority() + "이(가) 겹칩니다"));
            }
        }
        targetRepository.deleteAll(targetRepository.findByEvalCycleId(cycleId));
        targetRepository.flush();
        for (EvalTargetRules.Rule r : request.rules()) {
            targetRepository.save(new EvalCycleTarget(cycle, r.priority(), r.condIsOrgLead(),
                    r.condJobTitleId() == null ? null : jobTitleService.requireActive(r.condJobTitleId()),
                    r.condJobGradeId() == null ? null : jobGradeService.requireActive(r.condJobGradeId()),
                    r.condEmploymentTypeId() == null ? null : employmentTypeService.requireActive(r.condEmploymentTypeId()),
                    r.evalTemplateId() == null ? null : templateService.requireActive(r.evalTemplateId())));
        }
        targetRepository.flush();
        return rules(user, cycleId);
    }

    /** 지금 시작하면 만들어질 평가. 예정이 아니어도 볼 수 있다(지금 조직 기준 계산). */
    @Transactional(readOnly = true)
    public EvalTargetPreview preview(LoginUser user, long cycleId) {
        get(cycleId);
        return calculator.calculate(user.companyId(), cycleId);
    }

    /** 예정 → 진행. 미리보기와 같은 계산으로 대상자마다 평가 1건. 대상자가 0명(규칙이 없음 포함) → EVAL_NO_TARGET. */
    @Transactional
    public EvalCycleItem start(LoginUser user, long cycleId) {
        long cid = user.companyId();
        EvalCycle cycle = lock(cid, cycleId);
        if (cycle.getStatus() != EvalCycleStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 시작한 평가 기간입니다");
        }
        List<EvalTargetPreview.Target> targets = calculator.calculate(cid, cycleId).targets();
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.EVAL_NO_TARGET);
        }
        jdbc.batchUpdate("""
                        INSERT INTO evaluation (company_id, eval_cycle_id, target_employee_id, evaluator_id, eval_template_id)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                targets, targets.size(), (ps, t) -> {
                    ps.setLong(1, cid);
                    ps.setLong(2, cycleId);
                    ps.setLong(3, t.employeeId());
                    ps.setLong(4, t.evaluatorId());
                    ps.setLong(5, t.evalTemplateId());
                });
        cycle.start(OffsetDateTime.now(clock));
        cycleRepository.flush();
        return item(cid, cycleId, null);
    }

    /** 진행 → 종료. 미제출 건은 그 상태로 남는다(재오픈된 건만 종료 뒤에도 입력할 수 있다). */
    @Transactional
    public EvalCycleItem close(LoginUser user, long cycleId) {
        EvalCycle cycle = lock(user.companyId(), cycleId);
        if (cycle.getStatus() != EvalCycleStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "진행 중인 평가 기간만 마감할 수 있습니다");
        }
        cycle.close(OffsetDateTime.now(clock));
        cycleRepository.flush();
        return item(user.companyId(), cycleId, null);
    }

    // ------------------------------------------------------------------

    private EvalCycle get(long id) {
        return cycleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 같은 기간의 시작 · 규칙 교체 · 마감을 한 줄로 세운다. */
    private EvalCycle lock(long companyId, long id) {
        if (jdbc.queryForList("SELECT id FROM eval_cycle WHERE id = ? AND company_id = ? FOR UPDATE", Long.class, id,
                companyId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return get(id);
    }

    private static void checkDates(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw BusinessException.invalidFields(Map.of("endDate", "종료일은 시작일 이후여야 합니다"));
        }
    }

    /** 다른 평가 기간과 하루라도 겹치면 경고만(F-EVAL-01). */
    private String overlapWarning(long companyId, EvalCycle cycle) {
        Boolean overlaps = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM eval_cycle
                                       WHERE company_id = ? AND id <> ? AND start_date <= ? AND end_date >= ?)
                        """,
                Boolean.class, companyId, cycle.getId(), Date.valueOf(cycle.getEndDate()),
                Date.valueOf(cycle.getStartDate()));
        return Boolean.TRUE.equals(overlaps) ? "PERIOD_OVERLAP" : null;
    }

    private static String parseName(Object value) {
        if (!(value instanceof String s) || s.isBlank()) {
            throw BusinessException.invalidFields(Map.of("name", "이름을 입력하세요"));
        }
        if (s.strip().length() > 50) {
            throw BusinessException.invalidFields(Map.of("name", "50자 이하로 입력하세요"));
        }
        return s.strip();
    }

    private static LocalDate parseDate(String field, Object value) {
        try {
            return LocalDate.parse((String) value);
        } catch (ClassCastException | DateTimeParseException e) {
            throw BusinessException.invalidFields(Map.of(field, "날짜 형식(YYYY-MM-DD)이 아닙니다"));
        }
    }

    private EvalCycleItem item(long companyId, long id, String warning) {
        return jdbc.queryForObject(SELECT + " WHERE c.company_id = ? AND c.id = ?", (rs, i) -> map(rs, warning),
                companyId, id);
    }

    private static EvalCycleItem map(ResultSet rs, String warning) throws SQLException {
        OffsetDateTime startedAt = rs.getObject("started_at", OffsetDateTime.class);
        OffsetDateTime closedAt = rs.getObject("closed_at", OffsetDateTime.class);
        return new EvalCycleItem(rs.getLong("id"), rs.getString("name"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), EvalCycleStatus.valueOf(rs.getString("status")),
                startedAt == null ? null : startedAt.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime(),
                closedAt == null ? null : closedAt.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime(),
                rs.getLong("evaluation_count"), warning);
    }

    private static final String SELECT = """
            SELECT c.id, c.name, c.start_date, c.end_date, c.status::text AS status, c.started_at, c.closed_at,
                   (SELECT count(*) FROM evaluation e WHERE e.company_id = c.company_id AND e.eval_cycle_id = c.id)
                       AS evaluation_count
            FROM eval_cycle c
            """;
}
