package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.company.service.CompanyRegistrationService;
import com.nexuslabs.hr.domain.employee.dto.EmployeeDetail;
import com.nexuslabs.hr.domain.employee.dto.EmployeeUpdateRequest;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.domain.org.entity.EmploymentType;
import com.nexuslabs.hr.domain.org.service.EmploymentTypeService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.file.FileService;
import com.nexuslabs.hr.global.file.StoredFile;
import com.nexuslabs.hr.global.request.PatchRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 관리자 직원 정보 수정(F-EMP-03 의 관리자 범위). 보낸 필드만 바꾸고 null 은 비운다(API 설계서 1.1).
 * 퇴직자 → EMPLOYEE_RESIGNED. 감사 로그 없음(BR-AUDIT-001 목록 밖).
 */
@Service
public class EmployeeUpdateService {

    private static final Set<String> FIELDS = Set.of("name", "email", "phone", "address", "employmentTypeId",
            "payrollEligible", "nameEn", "birthDate", "gender", "emergencyName", "emergencyRelation", "emergencyPhone",
            "contractEndDate", "probationEndDate", "profileFileId", "hrMemo");
    private static final Set<String> NOT_NULL = Set.of("name", "email", "employmentTypeId", "payrollEligible");
    /** 발령으로만 바뀌는 항목(BR-EMP-001)과 사원번호(BR-EMP-006) — 요청에 있어도 무시한다(F-EMP-03). */
    private static final Set<String> IGNORED = Set.of("orgUnitId", "jobGradeId", "jobTitleId", "employeeNo");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final EmployeeRepository employeeRepository;
    private final EmployeeQueryService queryService;
    private final EmploymentTypeService employmentTypeService;
    private final FileService fileService;
    private final DocumentFiles documentFiles;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final JdbcTemplate jdbc;

    public EmployeeUpdateService(EmployeeRepository employeeRepository, EmployeeQueryService queryService,
                                 EmploymentTypeService employmentTypeService, FileService fileService,
                                 DocumentFiles documentFiles,
                                 ObjectMapper objectMapper, Validator validator, JdbcTemplate jdbc) {
        this.employeeRepository = employeeRepository;
        this.queryService = queryService;
        this.employmentTypeService = employmentTypeService;
        this.fileService = fileService;
        this.documentFiles = documentFiles;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.jdbc = jdbc;
    }

    /** 응답은 GET /employees/{id} 와 같다. */
    @Transactional
    public EmployeeDetail update(LoginUser user, long employeeId, Map<String, Object> patch) {
        Map<String, Object> fields = new LinkedHashMap<>(patch);
        fields.keySet().removeAll(IGNORED);
        PatchRequest.check(fields, FIELDS, NOT_NULL);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (employee.getStatus() == EmpStatus.RESIGNED) {
            throw new BusinessException(ErrorCode.EMPLOYEE_RESIGNED);
        }
        EmployeeUpdateRequest request = merge(employee, fields);

        String email = CompanyRegistrationService.normalizeEmail(request.email());
        // 이메일은 서비스 전체에서 유일하다(BR-TEN-002). JPA 조회는 @TenantId 로 내 회사만 보므로 JDBC 로 전체를 본다
        if (!email.equals(employee.getEmail()) && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM employee WHERE email = ? AND id <> ?)", Boolean.class, email,
                employeeId))) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
        }
        // 지금 고용형태가 비활성이어도 그대로 두는 것은 된다 — 새로 고를 때만 활성인지 본다
        EmploymentType employmentType = request.employmentTypeId().equals(employee.getEmploymentType().getId())
                ? employee.getEmploymentType()
                : employmentTypeService.requireActive(request.employmentTypeId());
        if (request.profileFileId() != null && !Objects.equals(request.profileFileId(), employee.getProfileFileId())) {
            requireImage(user, request.profileFileId());
        }

        employee.changeBasic(request.name().trim(), email, employmentType);
        employee.changeContact(blankToNull(request.phone()), blankToNull(request.address()));
        employee.changePersonal(blankToNull(request.nameEn()), request.birthDate(), request.gender());
        employee.changeEmergencyContact(blankToNull(request.emergencyName()),
                blankToNull(request.emergencyRelation()), blankToNull(request.emergencyPhone()));
        employee.changeContractDates(request.contractEndDate(), request.probationEndDate());
        employee.changeProfileFile(request.profileFileId());
        employee.changePayrollEligible(request.payrollEligible());
        employee.changeHrMemo(blankToNull(request.hrMemo()));
        // 응답은 JDBC 로 읽는다 — JPA 변경을 먼저 DB 에 내보낸다(백엔드 안내 6.1)
        employeeRepository.flush();
        return queryService.detail(user, employeeId);
    }

    private EmployeeUpdateRequest merge(Employee current, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("name", current.getName());
        merged.put("email", current.getEmail());
        merged.put("phone", current.getPhone());
        merged.put("address", current.getAddress());
        merged.put("employmentTypeId", current.getEmploymentType().getId());
        merged.put("payrollEligible", current.isPayrollEligible());
        merged.put("nameEn", current.getNameEn());
        merged.put("birthDate", current.getBirthDate());
        merged.put("gender", current.getGender());
        merged.put("emergencyName", current.getEmergencyName());
        merged.put("emergencyRelation", current.getEmergencyRelation());
        merged.put("emergencyPhone", current.getEmergencyPhone());
        merged.put("contractEndDate", current.getContractEndDate());
        merged.put("probationEndDate", current.getProbationEndDate());
        merged.put("profileFileId", current.getProfileFileId());
        merged.put("hrMemo", current.getHrMemo());
        merged.putAll(patch);
        EmployeeUpdateRequest request;
        try {
            request = objectMapper.convertValue(merged, EmployeeUpdateRequest.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<ConstraintViolation<EmployeeUpdateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.forEach(cv -> fields.putIfAbsent(cv.getPropertyPath().toString(), cv.getMessage()));
            throw BusinessException.invalidFields(fields);
        }
        return request;
    }

    /** 프로필 사진은 요청자가 올린, 아직 다른 곳에 쓰이지 않은 jpg · png 만(BR-FILE-001, DocumentFiles 와 같은 규칙). */
    private void requireImage(LoginUser user, long fileId) {
        documentFiles.requireLinkable(user, fileId, "profileFileId");
        StoredFile file = fileService.find(user.companyId(), fileId).orElseThrow();
        if (!IMAGE_TYPES.contains(file.contentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "프로필 사진은 jpg, png만 쓸 수 있습니다");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
