package com.nexuslabs.hr.domain.evaluation.service;

import com.nexuslabs.hr.domain.evaluation.dto.EvalTemplateDetail;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTemplateItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTemplateRequest;
import com.nexuslabs.hr.domain.evaluation.entity.EvalCriteria;
import com.nexuslabs.hr.domain.evaluation.entity.EvalQuestion;
import com.nexuslabs.hr.domain.evaluation.entity.EvalTemplate;
import com.nexuslabs.hr.domain.evaluation.repository.EvalTemplateRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.response.DeleteResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 평가 템플릿 관리(F-EVAL-02). 템플릿 + 항목 + 질문을 한 번에 통째로 저장한다. 시작된(진행 · 종료) 평가 기간에 쓰인 템플릿은
 * 수정할 수 없고(EVAL_TEMPLATE_IN_USE) 복사해서 고친다. 감사 로그 없음(BR-AUDIT-001 목록 밖).
 */
@Service
public class EvalTemplateService {

    private final EvalTemplateRepository repository;
    private final JdbcTemplate jdbc;

    public EvalTemplateService(EvalTemplateRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /** 이름 순. activeOnly 면 활성만(대상 규칙에서 고를 때). */
    @Transactional(readOnly = true)
    public List<EvalTemplateItem> list(LoginUser user, boolean activeOnly) {
        return jdbc.query("""
                        SELECT t.id, t.name, t.copied_from_id, t.is_active,
                               EXISTS (SELECT 1 FROM evaluation e WHERE e.company_id = t.company_id
                                                                    AND e.eval_template_id = t.id) AS in_use,
                               (SELECT count(*) FROM eval_criteria c WHERE c.company_id = t.company_id
                                                                      AND c.eval_template_id = t.id) AS criteria_count,
                               (SELECT count(*) FROM eval_question q JOIN eval_criteria c ON c.id = q.eval_criteria_id
                                WHERE c.company_id = t.company_id AND c.eval_template_id = t.id) AS question_count
                        FROM eval_template t
                        WHERE t.company_id = ? AND (t.is_active OR NOT ?)
                        ORDER BY t.name, t.id
                        """,
                (rs, i) -> new EvalTemplateItem(rs.getLong("id"), rs.getString("name"),
                        rs.getObject("copied_from_id", Long.class), rs.getBoolean("is_active"),
                        rs.getBoolean("in_use"), rs.getInt("criteria_count"), rs.getInt("question_count")),
                user.companyId(), activeOnly);
    }

    @Transactional(readOnly = true)
    public EvalTemplateDetail detail(LoginUser user, long id) {
        return toDetail(user.companyId(), get(id));
    }

    @Transactional
    public EvalTemplateDetail create(LoginUser user, EvalTemplateRequest request) {
        String name = request.name().trim();
        validate(request);
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        EvalTemplate template = new EvalTemplate(name, null);
        fill(template, request.criteria());
        repository.saveAndFlush(template);
        return toDetail(user.companyId(), template);
    }

    /** 통째로 교체. 시작된 평가 기간에 쓰였으면 EVAL_TEMPLATE_IN_USE. 예정 기간의 대상 규칙이 가리키는 것은 고칠 수 있다. */
    @Transactional
    public EvalTemplateDetail update(LoginUser user, long id, EvalTemplateRequest request) {
        EvalTemplate template = get(id);
        if (inUse(user.companyId(), id)) {
            throw new BusinessException(ErrorCode.EVAL_TEMPLATE_IN_USE);
        }
        String name = request.name().trim();
        validate(request);
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        template.rename(name);
        template.clearCriteria();
        repository.flush();          // 옛 항목 · 질문을 먼저 지운다
        fill(template, request.criteria());
        repository.flush();
        return toDetail(user.companyId(), template);
    }

    /** 복사본(원본을 copiedFromId 로 가리킴). 비활성 · 쓰인 템플릿도 복사할 수 있다. 201. */
    @Transactional
    public EvalTemplateDetail copy(LoginUser user, long id, String newName) {
        EvalTemplate source = get(id);
        String name = newName.trim();
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        EvalTemplate copy = new EvalTemplate(name, source);
        for (EvalCriteria c : source.getCriteria()) {
            EvalCriteria item = new EvalCriteria(copy, c.getCategory(), c.getName(), c.getWeight(), c.getSortOrder());
            c.getQuestions().forEach(q -> item.addQuestion(new EvalQuestion(item, q.getContent(), q.getSortOrder())));
            copy.addCriteria(item);
        }
        repository.saveAndFlush(copy);
        return toDetail(user.companyId(), copy);
    }

    /** 평가 기간에 쓰였거나(시작된 평가) 대상 규칙이 가리키면 비활성화(DEACTIVATED), 아니면 삭제(DELETED). */
    @Transactional
    public DeleteResult delete(LoginUser user, long id) {
        EvalTemplate template = get(id);
        Boolean referenced = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM evaluation WHERE company_id = ? AND eval_template_id = ?)
                            OR EXISTS (SELECT 1 FROM eval_cycle_target WHERE company_id = ? AND eval_template_id = ?)
                            OR EXISTS (SELECT 1 FROM eval_template WHERE company_id = ? AND copied_from_id = ?)
                        """,
                Boolean.class, user.companyId(), id, user.companyId(), id, user.companyId(), id);
        if (Boolean.TRUE.equals(referenced)) {
            template.deactivate();
            return DeleteResult.deactivated();
        }
        repository.delete(template);
        return DeleteResult.deleted();
    }

    /** 대상 규칙이 템플릿을 고를 때. 없으면 NOT_FOUND, 비활성이면 INACTIVE_REFERENCE. */
    @Transactional(readOnly = true)
    public EvalTemplate requireActive(long id) {
        EvalTemplate template = get(id);
        if (!template.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        return template;
    }

    // ------------------------------------------------------------------

    private EvalTemplate get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private boolean inUse(long companyId, long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM evaluation WHERE company_id = ? AND eval_template_id = ?)",
                Boolean.class, companyId, id));
    }

    /** 가중치 합 100(BR-EVAL-004), 항목마다 질문 1개 이상. 항목 이름은 템플릿 안에서 겹치지 않는다. */
    private static void validate(EvalTemplateRequest request) {
        int sum = request.criteria().stream().mapToInt(EvalTemplateRequest.Criteria::weight).sum();
        if (sum != 100) {
            throw new BusinessException(ErrorCode.EVAL_WEIGHT_SUM_INVALID, "항목 배점 합계가 100이어야 합니다(지금 " + sum + ")");
        }
        if (request.criteria().stream().anyMatch(c -> c.questions() == null || c.questions().isEmpty())) {
            throw new BusinessException(ErrorCode.EVAL_NO_QUESTION);
        }
        Set<String> names = new HashSet<>();
        for (EvalTemplateRequest.Criteria c : request.criteria()) {
            if (!names.add(c.name().trim())) {
                throw BusinessException.invalidFields(Map.of("criteria", "항목 이름이 겹칩니다: " + c.name().trim()));
            }
        }
    }

    /** 정렬 순서를 비우면 보낸 순서대로 1부터. */
    private static void fill(EvalTemplate template, List<EvalTemplateRequest.Criteria> criteria) {
        for (int i = 0; i < criteria.size(); i++) {
            EvalTemplateRequest.Criteria c = criteria.get(i);
            EvalCriteria item = new EvalCriteria(template, c.category().trim(), c.name().trim(),
                    c.weight().shortValue(), c.sortOrder() != null ? c.sortOrder() : i + 1);
            for (int j = 0; j < c.questions().size(); j++) {
                EvalTemplateRequest.Question q = c.questions().get(j);
                item.addQuestion(new EvalQuestion(item, q.content().trim(), q.sortOrder() != null ? q.sortOrder() : j + 1));
            }
            template.addCriteria(item);
        }
    }

    private EvalTemplateDetail toDetail(long companyId, EvalTemplate t) {
        List<EvalTemplateDetail.Criteria> criteria = t.getCriteria().stream()
                .sorted(Comparator.comparingInt(EvalCriteria::getSortOrder).thenComparing(EvalCriteria::getId))
                .map(c -> new EvalTemplateDetail.Criteria(c.getId(), c.getCategory(), c.getName(), c.getWeight(),
                        c.getSortOrder(), c.getQuestions().stream()
                        .sorted(Comparator.comparingInt(EvalQuestion::getSortOrder).thenComparing(EvalQuestion::getId))
                        .map(q -> new EvalTemplateDetail.Question(q.getId(), q.getContent(), q.getSortOrder()))
                        .toList()))
                .toList();
        return new EvalTemplateDetail(t.getId(), t.getName(), t.getCopiedFrom() == null ? null : t.getCopiedFrom().getId(),
                t.isActive(), inUse(companyId, t.getId()), criteria);
    }
}
