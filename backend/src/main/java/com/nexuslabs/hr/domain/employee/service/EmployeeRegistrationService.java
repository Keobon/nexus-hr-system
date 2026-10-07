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
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EmployeeRegistrationService(EmployeeRepository employeeRepository,
                                       EmploymentStatusHistoryRepository statusHistoryRepository,
                                       EmployeeNoGenerator employeeNoGenerator,
                                       EmployeeFieldValueService fieldValueService, OrgUnitService orgUnitService,
                                       JobGradeService jobGradeService, JobTitleService jobTitleService,
                                       EmploymentTypeService employmentTypeService, AccountService accountService,
                                       LeaveGrantService leaveGrantService, FileService fileService,
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
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public EmployeeCreateResponse register(LoginUser user, EmployeeCreateRequest request) {
        if (request.hireDate().isAfter(LocalDate.now(clock))) {
            throw BusinessException.invalidFields(Map.of("hireDate", "미래 날짜는 입력할 수 없습니다"));
        }
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
            requireImage(user.companyId(), request.profileFileId());
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

    private void requireImage(long companyId, long fileId) {
        StoredFile file = fileService.find(companyId, fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없습니다"));
        if (!IMAGE_TYPES.contains(file.contentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "프로필 사진은 jpg, png만 쓸 수 있습니다");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
