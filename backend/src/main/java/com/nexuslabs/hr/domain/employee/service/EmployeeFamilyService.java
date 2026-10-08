package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.dto.FamilyList;
import com.nexuslabs.hr.domain.employee.dto.FamilyMember;
import com.nexuslabs.hr.domain.employee.dto.FamilyRequest;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFamily;
import com.nexuslabs.hr.domain.employee.entity.FamilyRelation;
import com.nexuslabs.hr.domain.employee.repository.EmployeeFamilyRepository;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 가족 정보(F-EMP-09, BR-EMP-007). 소득세에 영향을 주므로 관리자(EMPLOYEE_MANAGE)만 고치고 감사 로그를 남긴다.
 * 본인은 조회만. 가족 정보는 실제로 삭제한다 — 확정된 명세서는 정산 당시 숫자를 저장해 두었으므로 영향이 없다.
 * 퇴직자는 조회만 된다(EMPLOYEE_RESIGNED).
 */
@Service
public class EmployeeFamilyService {

    private static final String AUDIT_TARGET = "EMPLOYEE_FAMILY";
    private static final Set<String> FIELDS = Set.of("name", "relation", "birthDate", "isTaxDependent", "isDisabled",
            "isCohabiting");
    private static final Set<String> NOT_NULL = Set.of("name", "relation", "isTaxDependent", "isDisabled",
            "isCohabiting");

    private final EmployeeFamilyRepository repository;
    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    public EmployeeFamilyService(EmployeeFamilyRepository repository, EmployeeRepository employeeRepository,
                                 JdbcTemplate jdbc, AuditLogger auditLogger, ObjectMapper objectMapper,
                                 Validator validator, Clock clock) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    /** 등록 순. 다른 회사 직원은 404. */
    @Transactional(readOnly = true)
    public FamilyList list(long companyId, long employeeId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee WHERE id = ? AND company_id = ?)",
                Boolean.class, employeeId, companyId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<FamilyMember> members = jdbc.query("""
                        SELECT id, name, relation::text AS relation, birth_date, is_tax_dependent, is_disabled, is_cohabiting
                        FROM employee_family WHERE company_id = ? AND employee_id = ? ORDER BY id
                        """,
                (rs, i) -> new FamilyMember(rs.getLong("id"), rs.getString("name"),
                        FamilyRelation.valueOf(rs.getString("relation")), rs.getObject("birth_date", LocalDate.class),
                        rs.getBoolean("is_tax_dependent"), rs.getBoolean("is_disabled"), rs.getBoolean("is_cohabiting")),
                companyId, employeeId);
        int dependents = (int) members.stream().filter(FamilyMember::taxDependent).count();
        int children = (int) members.stream()
                .filter(m -> m.taxDependent() && m.relation() == FamilyRelation.CHILD).count();
        return new FamilyList(dependents, children, members);
    }

    @Transactional
    public FamilyMember create(LoginUser user, long employeeId, FamilyRequest request) {
        Employee employee = writable(employeeId);
        checkBirthDate(request.birthDate());
        EmployeeFamily family = repository.saveAndFlush(new EmployeeFamily(employee, request.name().trim(),
                request.relation(), request.birthDate(), Boolean.TRUE.equals(request.taxDependent()),
                Boolean.TRUE.equals(request.disabled()), !Boolean.FALSE.equals(request.cohabiting())));
        FamilyMember created = FamilyMember.from(family);
        auditLogger.log(user, AuditAction.CREATE, AUDIT_TARGET, created.id(), null, audit(employeeId, created));
        return created;
    }

    /** 보낸 필드만 바꾼다. 생년월일만 비울 수 있다. */
    @Transactional
    public FamilyMember update(LoginUser user, long employeeId, long familyId, Map<String, Object> patch) {
        writable(employeeId);
        EmployeeFamily family = get(employeeId, familyId);
        FamilyMember before = FamilyMember.from(family);
        FamilyRequest request = merge(before, patch);
        checkBirthDate(request.birthDate());
        family.update(request.name().trim(), request.relation(), request.birthDate(), request.taxDependent(),
                request.disabled(), request.cohabiting());
        FamilyMember after = FamilyMember.from(family);
        auditLogger.log(user, AuditAction.UPDATE, AUDIT_TARGET, familyId, audit(employeeId, before),
                audit(employeeId, after));
        return after;
    }

    @Transactional
    public DeleteResult delete(LoginUser user, long employeeId, long familyId) {
        writable(employeeId);
        EmployeeFamily family = get(employeeId, familyId);
        FamilyMember before = FamilyMember.from(family);
        repository.delete(family);
        auditLogger.log(user, AuditAction.DELETE, AUDIT_TARGET, familyId, audit(employeeId, before), null);
        return DeleteResult.deleted();
    }

    private Employee writable(long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (employee.getStatus() == EmpStatus.RESIGNED) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        return employee;
    }

    /** 다른 직원의 가족이면 404. */
    private EmployeeFamily get(long employeeId, long familyId) {
        return repository.findById(familyId)
                .filter(f -> f.getEmployee().getId() == employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void checkBirthDate(LocalDate birthDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.now(clock))) {
            throw BusinessException.invalidFields(Map.of("birthDate", "오늘 이후 날짜는 입력할 수 없습니다"));
        }
    }

    private static Map<String, Object> audit(long employeeId, FamilyMember member) {
        return Map.of("employeeId", employeeId, "member", member);
    }

    private FamilyRequest merge(FamilyMember current, Map<String, Object> patch) {
        PatchRequest.check(patch, FIELDS, NOT_NULL);
        Map<String, Object> merged = new LinkedHashMap<>(
                objectMapper.convertValue(current, new TypeReference<Map<String, Object>>() {}));
        merged.remove("id");
        merged.putAll(patch);
        FamilyRequest request;
        try {
            request = objectMapper.convertValue(merged, FamilyRequest.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<ConstraintViolation<FamilyRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.forEach(cv -> fields.putIfAbsent(cv.getPropertyPath().toString(), cv.getMessage()));
            throw BusinessException.invalidFields(fields);
        }
        return request;
    }
}
