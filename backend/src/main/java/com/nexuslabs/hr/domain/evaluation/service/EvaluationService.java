package com.nexuslabs.hr.domain.evaluation.service;

import com.nexuslabs.hr.domain.approval.service.ApproverCalculator;
import com.nexuslabs.hr.domain.evaluation.dto.EvalAnswersRequest;
import com.nexuslabs.hr.domain.evaluation.dto.EvalScore;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTodoItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvaluationDetail;
import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleStatus;
import com.nexuslabs.hr.domain.evaluation.entity.Evaluation;
import com.nexuslabs.hr.domain.evaluation.entity.EvaluationStatus;
import com.nexuslabs.hr.domain.evaluation.repository.EvaluationRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 평가 입력 · 제출(F-EVAL-04)과 확정 · 재오픈(F-EVAL-06). 입력은 그 평가의 평가자만(권한 코드가 아니라 데이터로 판단),
 * 확정 · 재오픈은 EVAL_MANAGE. 같은 평가의 저장 · 제출 · 확정은 평가 행 잠금으로 한 줄로 세운다.
 * 점수는 질문 단위로만 저장하고 항목 점수 · 총점은 계산한다(BR-EVAL-004). 감사 로그 없음(BR-AUDIT-001 목록 밖).
 */
@Service
public class EvaluationService {

    private final EvaluationRepository repository;
    private final EvalScoreCalculator scoreCalculator;
    private final ApproverCalculator approverCalculator;
    private final PermissionReader permissionReader;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EvaluationService(EvaluationRepository repository, EvalScoreCalculator scoreCalculator,
                             ApproverCalculator approverCalculator, PermissionReader permissionReader, JdbcTemplate jdbc,
                             Clock clock) {
        this.repository = repository;
        this.scoreCalculator = scoreCalculator;
        this.approverCalculator = approverCalculator;
        this.permissionReader = permissionReader;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 내가 평가자인 건 — 진행 중 기간의 모든 건 + 재오픈 건(기간이 끝났어도). 기간 종료일 → 사원번호 순. */
    @Transactional(readOnly = true)
    public List<EvalTodoItem> todo(LoginUser user) {
        return jdbc.query("""
                        SELECT ev.id, c.id AS cycle_id, c.name AS cycle_name, c.end_date, e.id AS target_id, e.employee_no,
                               e.name AS target_name, o.name AS org_unit_name, t.name AS template_name,
                               ev.status::text AS status
                        FROM evaluation ev
                             JOIN eval_cycle c ON c.id = ev.eval_cycle_id AND c.company_id = ev.company_id
                             JOIN employee e ON e.id = ev.target_employee_id AND e.company_id = ev.company_id
                             JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                             JOIN eval_template t ON t.id = ev.eval_template_id AND t.company_id = ev.company_id
                        WHERE ev.company_id = ? AND ev.evaluator_id = ?
                          AND (c.status = 'IN_PROGRESS' OR ev.status = 'REOPENED')
                        ORDER BY c.end_date, c.id, e.employee_no, ev.id
                        """,
                (rs, i) -> new EvalTodoItem(rs.getLong("id"), rs.getLong("cycle_id"), rs.getString("cycle_name"),
                        rs.getObject("end_date", LocalDate.class), rs.getLong("target_id"), rs.getString("employee_no"),
                        rs.getString("target_name"), rs.getString("org_unit_name"), rs.getString("template_name"),
                        EvaluationStatus.valueOf(rs.getString("status"))),
                user.companyId(), user.employeeId());
    }

    /** 입력 폼. 평가자 · EVAL_MANAGE 만(아니면 FORBIDDEN), 다른 회사 평가는 404. */
    @Transactional(readOnly = true)
    public EvaluationDetail detail(LoginUser user, long id) {
        Row row = row(user.companyId(), id);
        if (row.evaluatorId() != user.employeeId() && !canManage(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return detail(user, row);
    }

    /**
     * 임시저장(미응답 허용) — 보낸 답으로 통째로 바꾸고 작성 전이면 작성중으로. 평가자만.
     * 제출완료 · 확정 → INVALID_STATE, 기간 종료(재오픈 아님) → EVAL_PERIOD_CLOSED. 템플릿에 없는 질문 · 같은 질문 두 번 → fields.answers.
     */
    @Transactional
    public EvaluationDetail saveAnswers(LoginUser user, long id, EvalAnswersRequest request) {
        Evaluation evaluation = lockForInput(user, id);
        Set<Long> questionIds = questionIds(user.companyId(), id);
        Set<Long> seen = new HashSet<>();
        for (EvalAnswersRequest.Answer a : request.answers()) {
            if (!questionIds.contains(a.questionId())) {
                throw BusinessException.invalidFields(Map.of("answers", "이 평가에 없는 질문입니다: " + a.questionId()));
            }
            if (!seen.add(a.questionId())) {
                throw BusinessException.invalidFields(Map.of("answers", "같은 질문에 두 번 답했습니다: " + a.questionId()));
            }
        }
        jdbc.update("DELETE FROM eval_answer WHERE company_id = ? AND evaluation_id = ?", user.companyId(), id);
        jdbc.batchUpdate("""
                        INSERT INTO eval_answer (company_id, evaluation_id, eval_question_id, score) VALUES (?, ?, ?, ?)
                        """,
                request.answers(), request.answers().size(), (ps, a) -> {
                    ps.setLong(1, user.companyId());
                    ps.setLong(2, id);
                    ps.setLong(3, a.questionId());
                    ps.setInt(4, a.score());
                });
        String comment = request.overallComment() == null || request.overallComment().isBlank()
                ? null : request.overallComment().strip();
        evaluation.saveDraft(comment);
        repository.flush();
        return detail(user, row(user.companyId(), id));
    }

    /** 제출 — 모든 질문에 답하고 종합의견이 있어야 한다(EVAL_INCOMPLETE). 평가자만. 상태 · 기간 검사는 임시저장과 같다. */
    @Transactional
    public EvaluationDetail submit(LoginUser user, long id) {
        Evaluation evaluation = lockForInput(user, id);
        Integer unanswered = jdbc.queryForObject("""
                        SELECT count(*) FROM eval_question q
                        JOIN eval_criteria c ON c.id = q.eval_criteria_id AND c.company_id = q.company_id
                        WHERE c.company_id = ? AND c.eval_template_id = ?
                          AND NOT EXISTS (SELECT 1 FROM eval_answer a WHERE a.company_id = q.company_id
                                                                         AND a.evaluation_id = ? AND a.eval_question_id = q.id)
                        """,
                Integer.class, user.companyId(), evaluation.getEvalTemplate().getId(), id);
        boolean noComment = evaluation.getOverallComment() == null || evaluation.getOverallComment().isBlank();
        if (unanswered != null && unanswered > 0 || noComment) {
            throw new BusinessException(ErrorCode.EVAL_INCOMPLETE,
                    Map.of("unansweredCount", unanswered == null ? 0 : unanswered, "overallCommentMissing", noComment));
        }
        evaluation.submit(OffsetDateTime.now(clock));
        repository.flush();
        return detail(user, row(user.companyId(), id));
    }

    /** 제출완료 → 확정(본인에게 공개). 아니면 INVALID_STATE. EVAL_MANAGE. */
    @Transactional
    public EvaluationDetail confirm(LoginUser user, long id) {
        Evaluation evaluation = lock(user.companyId(), id);
        if (evaluation.getStatus() != EvaluationStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "제출완료된 평가만 확정할 수 있습니다");
        }
        evaluation.confirm(user.employeeId(), OffsetDateTime.now(clock));
        repository.flush();
        return detail(user, row(user.companyId(), id));
    }

    /**
     * 평가자 변경(F-EVAL-06, 2026-10-10 추가) — 제출 전(작성 전 · 작성중 · 재오픈)만, 아니면 INVALID_STATE.
     * 새 평가자: 다른 회사 → 404, 재직 · 계정 활성이 아니거나 대상자 본인 → fields.evaluatorId(승인자 재지정과 같은 규칙).
     * 쓰던 점수 · 의견은 그대로 두고 새 평가자가 이어서 쓴다. EVAL_MANAGE.
     */
    @Transactional
    public EvaluationDetail changeEvaluator(LoginUser user, long id, long evaluatorId) {
        Evaluation evaluation = lock(user.companyId(), id);
        EvaluationStatus status = evaluation.getStatus();
        if (status == EvaluationStatus.SUBMITTED || status == EvaluationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "제출한 평가는 평가자를 바꿀 수 없습니다");
        }
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee WHERE id = ? AND company_id = ?)",
                Boolean.class, evaluatorId, user.companyId());
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "평가자로 지정할 직원을 찾을 수 없습니다");
        }
        if (evaluatorId == evaluation.getTargetEmployee().getId()) {
            throw BusinessException.invalidFields(Map.of("evaluatorId", "대상자 본인은 평가자가 될 수 없습니다"));
        }
        if (!approverCalculator.isCandidate(user.companyId(), evaluatorId)) {
            throw BusinessException.invalidFields(Map.of("evaluatorId", "재직 중이고 계정이 활성인 직원만 평가자가 될 수 있습니다"));
        }
        evaluation.changeEvaluator(evaluatorId);
        repository.flush();
        return detail(user, row(user.companyId(), id));
    }

    /** 확정 → 재오픈(사유 필수). 평가자가 다시 고쳐 제출하면 다시 확정한다. 기간이 끝났어도 입력할 수 있다. EVAL_MANAGE. */
    @Transactional
    public EvaluationDetail reopen(LoginUser user, long id, String reason) {
        Evaluation evaluation = lock(user.companyId(), id);
        if (evaluation.getStatus() != EvaluationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "확정된 평가만 재오픈할 수 있습니다");
        }
        evaluation.reopen(reason.strip());
        repository.flush();
        return detail(user, row(user.companyId(), id));
    }

    // ------------------------------------------------------------------

    private boolean canManage(LoginUser user) {
        return permissionReader.permissionsOf(user).containsKey(PermissionCode.EVAL_MANAGE);
    }

    private Evaluation lock(long companyId, long id) {
        if (jdbc.queryForList("SELECT id FROM evaluation WHERE id = ? AND company_id = ? FOR UPDATE", Long.class, id,
                companyId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 입력(임시저장 · 제출) 공통 — 평가자만, 입력할 수 있는 상태인지. */
    private Evaluation lockForInput(LoginUser user, long id) {
        Evaluation evaluation = lock(user.companyId(), id);
        if (evaluation.getEvaluatorId() != user.employeeId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 평가의 평가자만 입력할 수 있습니다");
        }
        EvaluationStatus status = evaluation.getStatus();
        if (status == EvaluationStatus.SUBMITTED || status == EvaluationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "제출한 평가는 재오픈 전까지 고칠 수 없습니다");
        }
        if (status != EvaluationStatus.REOPENED && evaluation.getEvalCycle().getStatus() != EvalCycleStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.EVAL_PERIOD_CLOSED);
        }
        return evaluation;
    }

    private Set<Long> questionIds(long companyId, long evaluationId) {
        return new HashSet<>(jdbc.queryForList("""
                        SELECT q.id FROM evaluation ev
                        JOIN eval_criteria c ON c.eval_template_id = ev.eval_template_id AND c.company_id = ev.company_id
                        JOIN eval_question q ON q.eval_criteria_id = c.id AND q.company_id = c.company_id
                        WHERE ev.company_id = ? AND ev.id = ?
                        """,
                Long.class, companyId, evaluationId));
    }

    private EvaluationDetail detail(LoginUser user, Row row) {
        long cid = user.companyId();
        Map<Long, Integer> answers = new HashMap<>();
        jdbc.query("SELECT eval_question_id, score FROM eval_answer WHERE company_id = ? AND evaluation_id = ?",
                rs -> {
                    answers.put(rs.getLong("eval_question_id"), rs.getInt("score"));
                },
                cid, row.id());
        Map<Long, List<EvaluationDetail.Question>> questions = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT q.id, q.eval_criteria_id, q.content FROM eval_question q
                        JOIN eval_criteria c ON c.id = q.eval_criteria_id AND c.company_id = q.company_id
                        WHERE c.company_id = ? AND c.eval_template_id = ?
                        ORDER BY q.sort_order, q.id
                        """,
                rs -> {
                    long qid = rs.getLong("id");
                    questions.computeIfAbsent(rs.getLong("eval_criteria_id"), k -> new ArrayList<>())
                            .add(new EvaluationDetail.Question(qid, rs.getString("content"), answers.get(qid)));
                },
                cid, row.templateId());
        List<EvalScore> scores = scoreCalculator.scores(cid, List.of(row.id())).getOrDefault(row.id(), List.of());
        List<EvaluationDetail.Criteria> criteria = scores.stream()
                .map(s -> new EvaluationDetail.Criteria(s.criteriaId(), s.category(), s.name(), s.weight(), s.itemScore(),
                        s.convertedScore(), questions.getOrDefault(s.criteriaId(), List.of())))
                .toList();
        boolean editable = row.evaluatorId() == user.employeeId()
                && (row.status() == EvaluationStatus.REOPENED
                    || (row.cycleStatus() == EvalCycleStatus.IN_PROGRESS
                        && (row.status() == EvaluationStatus.NOT_STARTED || row.status() == EvaluationStatus.IN_PROGRESS)));
        return new EvaluationDetail(row.id(), row.cycleId(), row.cycleName(), row.cycleStatus(), row.targetId(),
                row.targetName(), row.orgUnitName(), row.evaluatorId(), row.evaluatorName(), row.templateName(),
                row.status(), row.overallComment(), row.reopenReason(), row.submittedAt(), row.confirmedAt(), editable,
                criteria, EvalScoreCalculator.total(scores));
    }

    private Row row(long companyId, long id) {
        return jdbc.query("""
                        SELECT ev.id, ev.eval_cycle_id, c.name AS cycle_name, c.status::text AS cycle_status,
                               ev.target_employee_id, e.name AS target_name, o.name AS org_unit_name, ev.evaluator_id,
                               ve.name AS evaluator_name, ev.eval_template_id, t.name AS template_name,
                               ev.status::text AS status, ev.overall_comment, ev.reopen_reason, ev.submitted_at,
                               ev.confirmed_at
                        FROM evaluation ev
                             JOIN eval_cycle c ON c.id = ev.eval_cycle_id AND c.company_id = ev.company_id
                             JOIN employee e ON e.id = ev.target_employee_id AND e.company_id = ev.company_id
                             JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                             JOIN employee ve ON ve.id = ev.evaluator_id AND ve.company_id = ev.company_id
                             JOIN eval_template t ON t.id = ev.eval_template_id AND t.company_id = ev.company_id
                        WHERE ev.company_id = ? AND ev.id = ?
                        """,
                (rs, i) -> new Row(rs.getLong("id"), rs.getLong("eval_cycle_id"), rs.getString("cycle_name"),
                        EvalCycleStatus.valueOf(rs.getString("cycle_status")), rs.getLong("target_employee_id"),
                        rs.getString("target_name"), rs.getString("org_unit_name"), rs.getLong("evaluator_id"),
                        rs.getString("evaluator_name"), rs.getLong("eval_template_id"), rs.getString("template_name"),
                        EvaluationStatus.valueOf(rs.getString("status")), rs.getString("overall_comment"),
                        rs.getString("reopen_reason"), seoul(rs.getObject("submitted_at", OffsetDateTime.class)),
                        seoul(rs.getObject("confirmed_at", OffsetDateTime.class))),
                companyId, id).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }

    private record Row(long id, long cycleId, String cycleName, EvalCycleStatus cycleStatus, long targetId,
                       String targetName, String orgUnitName, long evaluatorId, String evaluatorName, long templateId,
                       String templateName, EvaluationStatus status, String overallComment, String reopenReason,
                       OffsetDateTime submittedAt, OffsetDateTime confirmedAt) {
    }
}
