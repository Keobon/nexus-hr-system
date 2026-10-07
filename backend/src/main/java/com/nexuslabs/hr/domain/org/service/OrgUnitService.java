package com.nexuslabs.hr.domain.org.service;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.org.dto.LeadSummary;
import com.nexuslabs.hr.domain.org.dto.OrgMemberResponse;
import com.nexuslabs.hr.domain.org.dto.OrgTreeNode;
import com.nexuslabs.hr.domain.org.dto.OrgUnitCreateRequest;
import com.nexuslabs.hr.domain.org.dto.OrgUnitMoveRequest;
import com.nexuslabs.hr.domain.org.dto.OrgUnitResponse;
import com.nexuslabs.hr.domain.org.entity.OrgUnit;
import com.nexuslabs.hr.domain.org.repository.OrgUnitRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 조직 관리와 조직도(F-ORG-01·02). 등록·수정은 JPA, 재귀 트리·인원 집계·다른 영역 참조 확인은 JDBC 로 한다.
 * JDBC 는 @TenantId 자동 필터가 없으므로 모든 쿼리에 company_id 조건을 직접 넣는다.
 * JPA 변경은 flush 전까지 DB 에 없으므로, JDBC 검사는 항상 엔티티를 바꾸기 전에 한다(백엔드 안내 6.1).
 */
@Service
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final PermissionReader permissionReader;

    public OrgUnitService(OrgUnitRepository orgUnitRepository, JdbcTemplate jdbc, EntityManager em,
                          PermissionReader permissionReader) {
        this.orgUnitRepository = orgUnitRepository;
        this.jdbc = jdbc;
        this.em = em;
        this.permissionReader = permissionReader;
    }

    /**
     * 조직도. 재귀 조회 한 번 + 인원 집계 한 번으로 만들고 조직마다 쿼리를 날리지 않는다.
     * 비활성 조직은 숨긴다 — ORG_MANAGE 가 있을 때만 includeInactive 로 함께 볼 수 있다.
     */
    @Transactional(readOnly = true)
    public OrgTreeNode tree(LoginUser user, boolean includeInactive) {
        boolean manager = permissionReader.permissionsOf(user).containsKey(PermissionCode.ORG_MANAGE);
        List<TreeRow> rows = jdbc.query("""
                        WITH RECURSIVE tree AS (
                            SELECT id, parent_id, name, level_name, is_active, lead_employee_id, monthly_budget, sort_order
                            FROM org_unit WHERE company_id = ? AND parent_id IS NULL
                            UNION
                            SELECT o.id, o.parent_id, o.name, o.level_name, o.is_active, o.lead_employee_id,
                                   o.monthly_budget, o.sort_order
                            FROM org_unit o JOIN tree t ON o.parent_id = t.id
                            WHERE o.company_id = ? AND (o.is_active OR ?)
                        )
                        SELECT t.*, e.name AS lead_name
                        FROM tree t LEFT JOIN employee e ON e.id = t.lead_employee_id AND e.company_id = ?
                        ORDER BY t.sort_order, t.id
                        """,
                (rs, i) -> new TreeRow(rs.getLong("id"), rs.getObject("parent_id", Long.class), rs.getString("name"),
                        rs.getString("level_name"), rs.getBoolean("is_active"),
                        rs.getObject("lead_employee_id", Long.class), rs.getString("lead_name"),
                        rs.getObject("monthly_budget", Long.class), rs.getInt("sort_order")),
                user.companyId(), user.companyId(), manager && includeInactive, user.companyId());

        Map<Long, Integer> directCounts = new HashMap<>();
        jdbc.query("""
                        SELECT org_unit_id, count(*) AS cnt FROM employee
                        WHERE company_id = ? AND status <> 'RESIGNED' GROUP BY org_unit_id
                        """,
                rs -> {
                    directCounts.put(rs.getLong("org_unit_id"), rs.getInt("cnt"));
                },
                user.companyId());

        TreeRow root = null;
        Map<Long, List<TreeRow>> childrenOf = new HashMap<>();
        for (TreeRow row : rows) {
            if (row.parentId() == null) {
                root = row;
            } else {
                childrenOf.computeIfAbsent(row.parentId(), k -> new ArrayList<>()).add(row);
            }
        }
        if (root == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toNode(root, childrenOf, directCounts, manager);
    }

    /** 그 조직의 직속 직원(퇴직자 제외). includeSub 면 하위 조직 직원까지. */
    @Transactional(readOnly = true)
    public List<OrgMemberResponse> members(LoginUser user, long orgUnitId, boolean includeSub) {
        get(orgUnitId);
        return jdbc.query("""
                        WITH RECURSIVE sub AS (
                            SELECT id FROM org_unit WHERE company_id = ? AND id = ?
                            UNION
                            SELECT o.id FROM org_unit o JOIN sub s ON o.parent_id = s.id
                            WHERE o.company_id = ? AND ?
                        )
                        SELECT e.id, e.name, g.name AS job_grade_name, t.name AS job_title_name, e.profile_file_id
                        FROM employee e
                             LEFT JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                             LEFT JOIN job_title t ON t.id = e.job_title_id AND t.company_id = e.company_id
                        WHERE e.company_id = ? AND e.status <> 'RESIGNED' AND e.org_unit_id IN (SELECT id FROM sub)
                        ORDER BY g.sort_order NULLS LAST, e.name, e.id
                        """,
                (rs, i) -> new OrgMemberResponse(rs.getLong("id"), rs.getString("name"), rs.getString("job_grade_name"),
                        rs.getString("job_title_name"), rs.getObject("profile_file_id", Long.class)),
                user.companyId(), orgUnitId, user.companyId(), includeSub, user.companyId());
    }

    @Transactional
    public OrgUnitResponse create(OrgUnitCreateRequest request) {
        OrgUnit parent = get(request.parentId());
        if (!parent.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        String name = request.name().trim();
        if (orgUnitRepository.existsByParentIdAndName(parent.getId(), name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        int sortOrder = request.sortOrder() != null ? request.sortOrder()
                : orgUnitRepository.maxSortOrderUnder(parent.getId()) + 1;
        OrgUnit orgUnit = new OrgUnit(parent, name, blankToNull(request.levelName()), sortOrder);
        orgUnit.changeMonthlyBudget(request.monthlyBudget());
        return toResponse(orgUnitRepository.save(orgUnit));
    }

    /**
     * 수정. 보낸 필드만 바꾸고, null 을 보내면 그 값을 비운다(API 설계서 1.1) — 그래서 "안 보냄"과 "null"을 구별하도록
     * 본문을 Map 으로 받는다. leadEmployeeId 가 null 이면 조직장을 해제한다. 상위 조직은 여기서 바꾸지 않는다(이동 API).
     */
    @Transactional
    public OrgUnitResponse update(LoginUser user, long orgUnitId, Map<String, Object> body) {
        OrgUnit orgUnit = get(orgUnitId);
        if (body.containsKey("name")) {
            String name = requireName(body.get("name"));
            if (!orgUnit.isRoot()
                    && orgUnitRepository.existsByParentIdAndNameAndIdNot(orgUnit.getParent().getId(), name, orgUnitId)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NAME);
            }
            orgUnit.rename(name);
        }
        if (body.containsKey("levelName")) {
            orgUnit.changeLevelName(optionalText(body.get("levelName"), 30, "단계 이름은 30자 이하로 입력하세요"));
        }
        if (body.containsKey("monthlyBudget")) {
            Long budget = optionalLong(body.get("monthlyBudget"), "예산은 0 이상의 숫자로 입력하세요");
            if (budget != null && budget < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "예산은 0 이상의 숫자로 입력하세요");
            }
            orgUnit.changeMonthlyBudget(budget);
        }
        if (body.containsKey("sortOrder")) {
            if (!(body.get("sortOrder") instanceof Integer sortOrder)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "정렬 순서는 숫자로 입력하세요");
            }
            orgUnit.changeSortOrder(sortOrder);
        }
        if (body.containsKey("leadEmployeeId")) {
            Long leadId = optionalLong(body.get("leadEmployeeId"), "조직장을 다시 선택하세요");
            if (leadId == null) {
                orgUnit.clearLead();
            } else {
                requireMember(user.companyId(), orgUnitId, leadId);
                orgUnit.assignLead(em.getReference(Employee.class, leadId));
            }
        }
        return toResponse(orgUnit);
    }

    /** 이동(상위 조직 변경). 자기 자신이나 자기 하위 조직 아래로는 옮길 수 없다(BR-ORG-002). */
    @Transactional
    public OrgUnitResponse move(LoginUser user, long orgUnitId, OrgUnitMoveRequest request) {
        OrgUnit orgUnit = get(orgUnitId);
        if (orgUnit.isRoot()) {
            throw new BusinessException(ErrorCode.ORG_ROOT_LOCKED);
        }
        OrgUnit newParent = get(request.parentId());
        if (isSelfOrDescendant(user.companyId(), orgUnitId, newParent.getId())) {
            throw new BusinessException(ErrorCode.ORG_CYCLE);
        }
        if (!newParent.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        if (!Objects.equals(orgUnit.getParent().getId(), newParent.getId())) {
            if (orgUnitRepository.existsByParentIdAndName(newParent.getId(), orgUnit.getName())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NAME);
            }
            orgUnit.moveTo(newParent);
        }
        return toResponse(orgUnit);
    }

    /** 비활성화. 재직·휴직 직원이나 활성 하위 조직이 남아 있으면 거부한다. 퇴직자만 남은 조직은 비활성화할 수 있다. */
    @Transactional
    public OrgUnitResponse deactivate(LoginUser user, long orgUnitId) {
        OrgUnit orgUnit = get(orgUnitId);
        requireNotRoot(orgUnit);
        if (orgUnit.isActive()) {
            requireEmpty(user.companyId(), orgUnitId);
            orgUnit.deactivate();
        }
        return toResponse(orgUnit);
    }

    @Transactional
    public OrgUnitResponse activate(long orgUnitId) {
        OrgUnit orgUnit = get(orgUnitId);
        if (!orgUnit.isRoot() && !orgUnit.getParent().isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE, "상위 조직을 먼저 다시 사용하도록 바꾸세요");
        }
        orgUnit.activate();
        return toResponse(orgUnit);
    }

    /**
     * 삭제(BR-ORG-001). 재직·휴직 직원이나 활성 하위 조직이 있으면 거부하고,
     * 그 밖에 쓰인 적이 있으면(퇴직자 · 비활성 하위 조직 · 발령 이력 · 급여명세서의 정산 당시 소속) 지우지 않고 비활성화한다.
     * 급여명세서의 org_unit_id_snap 은 FK 가 아니라서 DB 가 막아 주지 않는다 — 지우면 인건비 통계에서 조직 이름이 사라진다.
     */
    @Transactional
    public DeleteResult delete(LoginUser user, long orgUnitId) {
        OrgUnit orgUnit = get(orgUnitId);
        requireNotRoot(orgUnit);
        requireEmpty(user.companyId(), orgUnitId);
        Boolean used = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND org_unit_id = ?)
                            OR EXISTS (SELECT 1 FROM org_unit WHERE company_id = ? AND parent_id = ?)
                            OR EXISTS (SELECT 1 FROM assignment_history WHERE company_id = ? AND from_org_unit_id = ?)
                            OR EXISTS (SELECT 1 FROM assignment_history WHERE company_id = ? AND to_org_unit_id = ?)
                            OR EXISTS (SELECT 1 FROM paystub WHERE company_id = ? AND org_unit_id_snap = ?)
                        """,
                Boolean.class, user.companyId(), orgUnitId, user.companyId(), orgUnitId, user.companyId(), orgUnitId,
                user.companyId(), orgUnitId, user.companyId(), orgUnitId);
        if (Boolean.TRUE.equals(used)) {
            orgUnit.deactivate();
            return DeleteResult.deactivated();
        }
        orgUnitRepository.delete(orgUnit);
        return DeleteResult.deleted();
    }

    /**
     * 조직장 자동 해제(BR-ORG-003). 발령·퇴직 처리 뒤 같은 트랜잭션에서 부른다.
     * 그 직원이 조직장인 조직 중 ① 지금 소속이 그 조직이 아니거나 ② 퇴직이면 조직장을 비우고, 비운 조직 ID 를 돌려준다.
     * 휴직은 해당 없다. 호출하는 쪽이 방금 바꾼 소속·재직상태를 그대로 보도록 JPA 가 아니라 SQL 한 번으로 처리한다.
     */
    @Transactional
    public List<Long> releaseLeadsIfLeft(long companyId, long employeeId) {
        return jdbc.queryForList("""
                        UPDATE org_unit o SET lead_employee_id = NULL, updated_at = now()
                        FROM employee e
                        WHERE o.company_id = ? AND o.lead_employee_id = ?
                          AND e.id = o.lead_employee_id AND e.company_id = o.company_id
                          AND (e.org_unit_id <> o.id OR e.status = 'RESIGNED')
                        RETURNING o.id
                        """,
                Long.class, companyId, employeeId);
    }

    /** 직원을 배치·발령할 조직을 가져온다. 없으면 NOT_FOUND, 비활성이면 INACTIVE_REFERENCE(F-ORG-01). */
    @Transactional(readOnly = true)
    public OrgUnit requireActive(long orgUnitId) {
        OrgUnit orgUnit = get(orgUnitId);
        if (!orgUnit.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        return orgUnit;
    }

    private OrgUnit get(long orgUnitId) {
        return orgUnitRepository.findById(orgUnitId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static void requireNotRoot(OrgUnit orgUnit) {
        if (orgUnit.isRoot()) {
            throw new BusinessException(ErrorCode.ORG_ROOT_LOCKED);
        }
    }

    /** 재직·휴직 직원과 활성 하위 조직이 없어야 한다. */
    private void requireEmpty(long companyId, long orgUnitId) {
        Boolean occupied = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM employee
                                       WHERE company_id = ? AND org_unit_id = ? AND status <> 'RESIGNED')
                            OR EXISTS (SELECT 1 FROM org_unit WHERE company_id = ? AND parent_id = ? AND is_active)
                        """,
                Boolean.class, companyId, orgUnitId, companyId, orgUnitId);
        if (Boolean.TRUE.equals(occupied)) {
            throw new BusinessException(ErrorCode.ORG_HAS_MEMBERS);
        }
    }

    /** 조직장은 그 조직 직속이고 퇴직하지 않은 직원이어야 한다. 다른 회사 직원은 없는 것으로 본다(404). */
    private void requireMember(long companyId, long orgUnitId, long employeeId) {
        Map<String, Object> employee = jdbc.queryForList(
                        "SELECT org_unit_id, status::text AS status FROM employee WHERE id = ? AND company_id = ?",
                        employeeId, companyId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if ("RESIGNED".equals(employee.get("status"))) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        if (((Number) employee.get("org_unit_id")).longValue() != orgUnitId) {
            throw new BusinessException(ErrorCode.ORG_LEAD_NOT_MEMBER);
        }
    }

    private boolean isSelfOrDescendant(long companyId, long orgUnitId, long candidateId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        WITH RECURSIVE sub AS (
                            SELECT id FROM org_unit WHERE company_id = ? AND id = ?
                            UNION
                            SELECT o.id FROM org_unit o JOIN sub s ON o.parent_id = s.id WHERE o.company_id = ?
                        )
                        SELECT EXISTS (SELECT 1 FROM sub WHERE id = ?)
                        """,
                Boolean.class, companyId, orgUnitId, companyId, candidateId));
    }

    private static OrgTreeNode toNode(TreeRow row, Map<Long, List<TreeRow>> childrenOf, Map<Long, Integer> directCounts,
                                      boolean manager) {
        List<OrgTreeNode> children = childrenOf.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toNode(child, childrenOf, directCounts, manager))
                .toList();
        int directCount = directCounts.getOrDefault(row.id(), 0);
        int totalCount = directCount + children.stream().mapToInt(OrgTreeNode::totalCount).sum();
        LeadSummary lead = row.leadId() != null ? new LeadSummary(row.leadId(), row.leadName()) : null;
        return new OrgTreeNode(row.id(), row.name(), row.levelName(), row.active(), lead, row.sortOrder(),
                manager ? Optional.ofNullable(row.monthlyBudget()) : null, directCount, totalCount, children);
    }

    private static OrgUnitResponse toResponse(OrgUnit orgUnit) {
        Employee lead = orgUnit.getLeadEmployee();
        return new OrgUnitResponse(orgUnit.getId(), orgUnit.isRoot() ? null : orgUnit.getParent().getId(),
                orgUnit.getName(), orgUnit.getLevelName(),
                lead != null ? new LeadSummary(lead.getId(), lead.getName()) : null,
                orgUnit.getMonthlyBudget(), orgUnit.getSortOrder(), orgUnit.isActive());
    }

    private static String requireName(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.trim().length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "조직 이름은 1자 이상 50자 이하로 입력하세요");
        }
        return text.trim();
    }

    private static String optionalText(Object value, int maxLength, String message) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.trim().length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return blankToNull(text);
    }

    private static Long optionalLong(Object value, String message) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private record TreeRow(long id, Long parentId, String name, String levelName, boolean active, Long leadId,
                           String leadName, Long monthlyBudget, int sortOrder) {
    }
}
