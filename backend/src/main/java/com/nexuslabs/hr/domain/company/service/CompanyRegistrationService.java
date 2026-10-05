package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.account.service.LoginNext;
import com.nexuslabs.hr.domain.company.dto.CompanyRegisterRequest;
import com.nexuslabs.hr.domain.company.dto.CompanyRegisterResponse;
import com.nexuslabs.hr.domain.employee.service.EmployeeNoGenerator;
import com.nexuslabs.hr.global.auth.JwtProvider;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

/**
 * 회사 등록(F-COMP-01). 한 트랜잭션: 회사 → 기본값 → 최상위 조직 → 관리자 직원·이력·계정 → 토큰.
 * 로그인 전이라 회사가 정해지지 않았으므로 JPA(@TenantId)가 아니라 company_id 를 직접 넣는 JDBC로 만든다.
 */
@Service
public class CompanyRegistrationService {

    private final JdbcTemplate jdbc;
    private final CompanyBootstrapService bootstrapService;
    private final EmployeeNoGenerator employeeNoGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    public CompanyRegistrationService(JdbcTemplate jdbc, CompanyBootstrapService bootstrapService,
                                      EmployeeNoGenerator employeeNoGenerator, PasswordEncoder passwordEncoder,
                                      JwtProvider jwtProvider, Clock clock) {
        this.jdbc = jdbc;
        this.bootstrapService = bootstrapService;
        this.employeeNoGenerator = employeeNoGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.clock = clock;
    }

    @Transactional
    public CompanyRegisterResponse register(CompanyRegisterRequest request) {
        CompanyRegisterRequest.CompanyInfo c = request.company();
        CompanyRegisterRequest.AdminInfo a = request.admin();
        String businessRegNo = formatBusinessRegNo(c.businessRegNo());
        String adminEmail = normalizeEmail(a.email());

        if (exists("SELECT EXISTS (SELECT 1 FROM company WHERE business_reg_no = ?)", businessRegNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_REG_NO_DUPLICATE);
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM employee WHERE email = ?)", adminEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
        }

        LocalDate today = LocalDate.now(clock);
        long companyId = jdbc.queryForObject("""
                        INSERT INTO company (name, business_reg_no, ceo_name, address, phone, email)
                        VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                        """,
                Long.class, c.name().trim(), businessRegNo, c.ceoName().trim(), c.address().trim(), c.phone().trim(),
                c.email().trim());
        CompanyBootstrapService.Defaults defaults = bootstrapService.createDefaults(companyId, c.name().trim(), today);

        String employeeNo = employeeNoGenerator.next(companyId, today.getYear());
        long employeeId = jdbc.queryForObject("""
                        INSERT INTO employee (company_id, employee_no, name, email, hire_date, org_unit_id, employment_type_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                        """,
                Long.class, companyId, employeeNo, a.name().trim(), adminEmail, Date.valueOf(today),
                defaults.rootOrgUnitId(), defaults.regularEmploymentTypeId());
        jdbc.update("""
                        INSERT INTO employment_status_history (company_id, employee_id, status, effective_date, reason)
                        VALUES (?, ?, 'ACTIVE', ?, '입사')
                        """,
                companyId, employeeId, Date.valueOf(today));
        jdbc.update("""
                        INSERT INTO account (company_id, employee_id, password_hash, role_id, must_change_password)
                        VALUES (?, ?, ?, ?, FALSE)
                        """,
                companyId, employeeId, passwordEncoder.encode(a.password()), defaults.superAdminRoleId());

        return new CompanyRegisterResponse(jwtProvider.issue(employeeId, companyId), companyId, employeeId,
                LoginNext.SETUP_WIZARD);
    }

    /** 숫자 10자리 또는 000-00-00000 → 000-00-00000 (DB 형식). */
    static String formatBusinessRegNo(String input) {
        String digits = input.replaceAll("\\D", "");
        return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
    }

    /** 로그인 ID 비교를 위해 앞뒤 공백을 빼고 소문자로 저장한다. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean exists(String sql, Object arg) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, arg));
    }
}
