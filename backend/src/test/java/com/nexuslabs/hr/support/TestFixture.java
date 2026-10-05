package com.nexuslabs.hr.support;

import com.nexuslabs.hr.domain.company.dto.CompanyRegisterRequest;
import com.nexuslabs.hr.domain.company.dto.CompanyRegisterResponse;
import com.nexuslabs.hr.domain.company.service.CompanyRegistrationService;
import com.nexuslabs.hr.domain.employee.service.EmployeeNoGenerator;
import com.nexuslabs.hr.global.auth.JwtProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트 데이터 헬퍼. 회사는 실제 회사 등록(CompanyBootstrapService)으로 만들어 기본값이 운영과 같다.
 * 테스트 클래스에 @Transactional 을 붙여 끝나면 롤백한다(JPA 조회 테스트는 백엔드 안내 7장 주의 참고).
 */
@Component
public class TestFixture {

    public static final String PASSWORD = "Passw0rd!";

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private final CompanyRegistrationService registrationService;
    private final EmployeeNoGenerator employeeNoGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JdbcTemplate jdbc;
    private String passwordHash;

    public TestFixture(CompanyRegistrationService registrationService, EmployeeNoGenerator employeeNoGenerator,
                       PasswordEncoder passwordEncoder, JwtProvider jwtProvider, JdbcTemplate jdbc) {
        this.registrationService = registrationService;
        this.employeeNoGenerator = employeeNoGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.jdbc = jdbc;
    }

    /** 회사 등록. 관리자 비밀번호는 PASSWORD. */
    public Company company(String name) {
        long n = SEQ.incrementAndGet();
        String regNo = "%010d".formatted(n % 10_000_000_000L);
        String adminEmail = "admin" + n + "@test.example";
        CompanyRegisterResponse res = registrationService.register(new CompanyRegisterRequest(
                new CompanyRegisterRequest.CompanyInfo(name, regNo, "대표", "서울", "02-000-0000", "c" + n + "@test.example"),
                new CompanyRegisterRequest.AdminInfo("관리자", adminEmail, PASSWORD)));
        long rootOrgUnitId = jdbc.queryForObject(
                "SELECT id FROM org_unit WHERE company_id = ? AND parent_id IS NULL", Long.class, res.companyId());
        return new Company(res.companyId(), res.employeeId(), adminEmail, rootOrgUnitId);
    }

    /** 하위 조직을 만든다. */
    public long orgUnit(long companyId, long parentId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO org_unit (company_id, parent_id, name) VALUES (?, ?, ?) RETURNING id",
                Long.class, companyId, parentId, name);
    }

    public void lead(long companyId, long orgUnitId, long employeeId) {
        jdbc.update("UPDATE org_unit SET lead_employee_id = ? WHERE id = ? AND company_id = ?",
                employeeId, orgUnitId, companyId);
    }

    /** 직원 + 계정. roleName 은 기본 역할 이름(최고 관리자 · 인사 담당 · 경영진 · 직원). 비밀번호는 PASSWORD. */
    public Employee employee(long companyId, long orgUnitId, String roleName, boolean mustChangePassword) {
        long n = SEQ.incrementAndGet();
        String email = "e" + n + "@test.example";
        LocalDate hireDate = LocalDate.of(2024, 3, 1);
        long typeId = jdbc.queryForObject(
                "SELECT id FROM employment_type WHERE company_id = ? AND name = '정규직'", Long.class, companyId);
        long employeeId = jdbc.queryForObject("""
                        INSERT INTO employee (company_id, employee_no, name, email, hire_date, org_unit_id, employment_type_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                        """,
                Long.class, companyId, employeeNoGenerator.next(companyId, hireDate.getYear()), "직원" + n, email,
                Date.valueOf(hireDate), orgUnitId, typeId);
        long roleId = jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?",
                Long.class, companyId, roleName);
        jdbc.update("""
                        INSERT INTO account (company_id, employee_id, password_hash, role_id, must_change_password)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                companyId, employeeId, hash(), roleId, mustChangePassword);
        return new Employee(employeeId, companyId, email);
    }

    public String token(long employeeId, long companyId) {
        return "Bearer " + jwtProvider.issue(employeeId, companyId);
    }

    private String hash() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        return passwordHash;
    }

    public record Company(long id, long adminId, String adminEmail, long rootOrgUnitId) {
    }

    public record Employee(long id, long companyId, String email) {
    }
}
