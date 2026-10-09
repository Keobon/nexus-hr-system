package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.account.service.AccountService;
import com.nexuslabs.hr.domain.company.service.CompanyRegistrationService;
import com.nexuslabs.hr.domain.employee.dto.EmployeeCreateRequest;
import com.nexuslabs.hr.domain.employee.dto.EmployeeCreateResponse;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.entity.EmploymentStatusHistory;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.domain.employee.repository.EmploymentStatusHistoryRepository;
import com.nexuslabs.hr.domain.leave.service.LeaveGrantService;
import com.nexuslabs.hr.domain.org.entity.EmploymentType;
import com.nexuslabs.hr.domain.org.entity.JobGrade;
import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.domain.org.entity.OrgUnit;
import com.nexuslabs.hr.domain.org.service.EmploymentTypeService;
import com.nexuslabs.hr.domain.org.service.JobGradeService;
import com.nexuslabs.hr.domain.org.service.JobTitleService;
import com.nexuslabs.hr.domain.org.service.OrgUnitService;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.file.FileService;
import com.nexuslabs.hr.domain.company.service.CompanyBootstrapService;
import com.nexuslabs.hr.global.permission.PermissionReader;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.file.StoredFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 직원 등록(F-EMP-01). 한 트랜잭션에서 사원번호 → 직원 → 재직상태 이력(입사) → 추가 항목 값 → 계정 → 입사 휴가 부여를 만든다.
 * 중간에 하나라도 실패하면 직원도 남지 않는다.
 */
@Service
public class EmployeeRegistrationService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final EmployeeRepository employeeRepository;
    private final EmploymentStatusHistoryRepository statusHistoryRepository;
    private final EmployeeNoGenerator employeeNoGenerator;
    private final EmployeeFieldValueService fieldValueService;
    private final OrgUnitService orgUnitService;
    private final JobGradeService jobGradeService;
    private final JobTitleService jobTitleService;
    private final EmploymentTypeService employmentTypeService;
    private final AccountService accountService;
    private final LeaveGrantService leaveGrantService;
    private final FileService fileService;
    private final DocumentFiles documentFiles;
    private final PermissionReader permissionReader;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EmployeeRegistrationService(EmployeeRepository employeeRepository,
                                       EmploymentStatusHistoryRepository statusHistoryRepository,
                                       EmployeeNoGenerator employeeNoGenerator,
                                       EmployeeFieldValueService fieldValueService, OrgUnitService orgUnitService,
                                       JobGradeService jobGradeService, JobTitleService jobTitleService,
                                       EmploymentTypeService employmentTypeService, AccountService accountService,
                                       LeaveGrantService leaveGrantService, FileService fileService,
                                       DocumentFiles documentFiles, PermissionReader permissionReader,
                                       JdbcTemplate jdbc, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.employeeNoGenerator = employeeNoGenerator;
        this.fieldValueService = fieldValueService;
        this.orgUnitService = orgUnitService;
        this.jobGradeService = jobGradeService;
        this.jobTitleService = jobTitleService;
        this.employmentTypeService = employmentTypeService;
        this.accountService = accountService;
        this.leaveGrantService = leaveGrantService;
        this.fileService = fileService;
        this.documentFiles = documentFiles;
        this.permissionReader = permissionReader;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public EmployeeCreateResponse register(LoginUser user, EmployeeCreateRequest request) {
        if (request.hireDate().isAfter(LocalDate.now(clock))) {
            throw BusinessException.invalidFields(Map.of("hireDate", "미래 날짜는 입력할 수 없습니다"));
        }
        requireRoleGrant(user, request.roleId());
        String email = CompanyRegistrationService.normalizeEmail(request.email());
        // 이메일은 서비스 전체에서 유일하다(BR-TEN-002). JPA 조회는 @TenantId 로 내 회사만 보므로 JDBC 로 전체를 본다
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM employee WHERE email = ?)", Boolean.class, email))) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
        }

        OrgUnit orgUnit = orgUnitService.requireActive(request.orgUnitId());
        JobGrade jobGrade = jobGradeService.requireActive(request.jobGradeId());
        JobTitle jobTitle = request.jobTitleId() == null ? null : jobTitleService.requireActive(request.jobTitleId());
        EmploymentType employmentType = employmentTypeService.requireActive(request.employmentTypeId());
        if (request.profileFileId() != null) {
            requireImage(user, request.profileFileId());
        }

        Employee employee = new Employee(employeeNo(user.companyId(), request), request.name().trim(), email,
                request.hireDate(), orgUnit, jobGrade, jobTitle, employmentType);
        employee.changeContact(blankToNull(request.phone()), blankToNull(request.address()));
        employee.changePersonal(blankToNull(request.nameEn()), request.birthDate(), request.gender());
        employee.changeEmergencyContact(blankToNull(request.emergencyName()), blankToNull(request.emergencyRelation()),
                blankToNull(request.emergencyPhone()));
        employee.changeContractDates(request.contractEndDate(), request.probationEndDate());
        employee.changeProfileFile(request.profileFileId());
        employee.changePayrollEligible(!Boolean.FALSE.equals(request.payrollEligible()));
        employee.changeHrMemo(blankToNull(request.hrMemo()));
        employeeRepository.save(employee);
        statusHistoryRepository.save(new EmploymentStatusHistory(employee, EmpStatus.ACTIVE, request.hireDate(), "입사",
                user.employeeId()));
        fieldValueService.saveForNewEmployee(employee, request.fieldValues());

        // 계정 생성과 휴가 부여는 JDBC 로 직원 행을 읽는다 — JPA 변경을 먼저 DB 에 내보낸다(백엔드 안내 6.1)
        employeeRepository.flush();
        String temporaryPassword = accountService.createForEmployee(user.companyId(), employee.getId(), request.roleId());
        List<EmployeeCreateResponse.LeaveGrant> leaveGrants =
                leaveGrantService.grantOnHire(user.companyId(), employee.getId()).stream()
                        .map(g -> new EmployeeCreateResponse.LeaveGrant(g.leaveTypeName(), g.leaveYear(), g.grantType(),
                                g.days()))
                        .toList();
        return new EmployeeCreateResponse(employee.getId(), employee.getEmployeeNo(), employee.getName(),
                employee.getStatus(), temporaryPassword, leaveGrants);
    }

    /** 비우면 자동 채번, 직접 넣으면 회사 안에서 겹치지 않아야 한다(BR-EMP-006). */
    private String employeeNo(long companyId, EmployeeCreateRequest request) {
        String given = blankToNull(request.employeeNo());
        if (given == null) {
            return employeeNoGenerator.next(companyId, request.hireDate().getYear());
        }
        if (employeeRepository.existsByEmployeeNo(given)) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NO_DUPLICATE);
        }
        return given;
    }

    /** 프로필 사진은 요청자가 올린, 아직 다른 곳에 쓰이지 않은 jpg · png 만(BR-FILE-001, DocumentFiles 와 같은 규칙). */
    private void requireImage(LoginUser user, long fileId) {
        documentFiles.requireLinkable(user, fileId, "profileFileId");
        StoredFile file = fileService.find(user.companyId(), fileId).orElseThrow();
        if (!IMAGE_TYPES.contains(file.contentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "프로필 사진은 jpg, png만 쓸 수 있습니다");
        }
    }

    /**
     * 기본 역할("직원")이 아닌 역할을 주려면 ROLE_MANAGE(계정 역할 부여)가 있어야 한다 — 인사 담당이 직원을
     * 최고 관리자 같은 높은 역할로 등록하지 못하게(2026-10-10, 역할 분담 2.3 I 69번).
     */
    private void requireRoleGrant(LoginUser user, Long roleId) {
        if (roleId == null || permissionReader.permissionsOf(user).containsKey(PermissionCode.ROLE_MANAGE)) {
            return;
        }
        Long defaultRole = jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?", Long.class,
                user.companyId(), CompanyBootstrapService.EMPLOYEE_ROLE);
        if (!roleId.equals(defaultRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "기본 역할이 아닌 역할을 주려면 역할 관리 권한이 필요합니다");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
