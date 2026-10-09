package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.dto.MyProfileResponse;
import com.nexuslabs.hr.domain.employee.dto.MyProfileUpdateRequest;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.file.FileService;
import com.nexuslabs.hr.global.file.StoredFile;
import com.nexuslabs.hr.global.request.PatchRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 본인 정보 수정(F-EMP-03 의 본인 범위) — 전화 · 주소 · 영문명 · 비상연락처 · 프로필 사진.
 * 토큰의 직원 ID 로만 고친다(BR-AUTH-001). 관리자만 고칠 수 있는 항목이 섞이면 FORBIDDEN.
 */
@Service
public class MyProfileService {

    private static final Set<String> FIELDS = Set.of("phone", "address", "nameEn", "emergencyName",
            "emergencyRelation", "emergencyPhone", "profileFileId");
    /** F-EMP-03 에서 관리자만 고치는 항목 — 모르는 항목(VALIDATION_ERROR)과 구별해 FORBIDDEN 으로 답한다. */
    private static final Set<String> ADMIN_ONLY = Set.of("name", "email", "employmentTypeId", "payrollEligible",
            "birthDate", "gender", "contractEndDate", "probationEndDate", "hrMemo");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final EmployeeRepository employeeRepository;
    private final EmployeeQueryService queryService;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public MyProfileService(EmployeeRepository employeeRepository, EmployeeQueryService queryService,
                            FileService fileService, ObjectMapper objectMapper, Validator validator) {
        this.employeeRepository = employeeRepository;
        this.queryService = queryService;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** 보낸 필드만 바꾸고 null 은 비운다(API 설계서 1.1). 응답은 GET /me/profile 과 같다. */
    @Transactional
    public MyProfileResponse update(LoginUser user, Map<String, Object> patch) {
        if (patch.keySet().stream().anyMatch(ADMIN_ONLY::contains)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 고칠 수 없는 항목입니다");
        }
        PatchRequest.check(patch, FIELDS, Set.of());
        Employee employee = employeeRepository.findById(user.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        MyProfileUpdateRequest request = merge(employee, patch);
        if (request.profileFileId() != null && !Objects.equals(request.profileFileId(), employee.getProfileFileId())) {
            requireMyImage(user, request.profileFileId());
        }

        employee.changeContact(blankToNull(request.phone()), blankToNull(request.address()));
        employee.changePersonal(blankToNull(request.nameEn()), employee.getBirthDate(), employee.getGender());
        employee.changeEmergencyContact(blankToNull(request.emergencyName()),
                blankToNull(request.emergencyRelation()), blankToNull(request.emergencyPhone()));
        employee.changeProfileFile(request.profileFileId());
        // 응답은 JDBC 로 읽는다 — JPA 변경을 먼저 DB 에 내보낸다(백엔드 안내 6.1)
        employeeRepository.flush();
        return queryService.myProfile(user);
    }

    private MyProfileUpdateRequest merge(Employee current, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("phone", current.getPhone());
        merged.put("address", current.getAddress());
        merged.put("nameEn", current.getNameEn());
        merged.put("emergencyName", current.getEmergencyName());
        merged.put("emergencyRelation", current.getEmergencyRelation());
        merged.put("emergencyPhone", current.getEmergencyPhone());
        merged.put("profileFileId", current.getProfileFileId());
        merged.putAll(patch);
        MyProfileUpdateRequest request;
        try {
            request = objectMapper.convertValue(merged, MyProfileUpdateRequest.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<ConstraintViolation<MyProfileUpdateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.forEach(cv -> fields.putIfAbsent(cv.getPropertyPath().toString(), cv.getMessage()));
            throw BusinessException.invalidFields(fields);
        }
        return request;
    }

    /** 프로필 사진은 본인이 올린 jpg · png 만 건다. 남의 파일 ID · 없는 ID 는 구별하지 않는다. */
    private void requireMyImage(LoginUser user, long fileId) {
        StoredFile file = fileService.find(user.companyId(), fileId)
                .filter(f -> f.uploadedBy() != null && f.uploadedBy() == user.employeeId())
                .orElseThrow(() -> BusinessException.invalidFields(Map.of("profileFileId", "파일을 다시 올려 주세요")));
        if (!IMAGE_TYPES.contains(file.contentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "프로필 사진은 jpg, png만 쓸 수 있습니다");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
