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
 * JDBC 로 확인하는 테스트는 클래스에 @Transactional 을 붙여 끝나면 롤백한다.
 * JPA 조회를 MockMvc 로 확인하는 테스트는 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장) —
 * 그래서 번호(사업자번호·이메일)는 DB에 남아 있는 값과 겹치지 않게 그 최대값 다음부터 쓴다.
 */
@Component
public class TestFixture {

    public static final String PASSWORD = "Passw0rd!";

    /** 처음 쓸 때 DB의 최대값으로 맞춘다. 여러 테스트 컨텍스트가 함께 쓰도록 static. */
    private static final AtomicLong SEQ = new AtomicLong(-1);

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
        long n = nextSeq();
        String regNo = regNo(n);
        String adminEmail = "admin" + n + "@test.example";
        CompanyRegisterResponse res = registrationService.register(new CompanyRegisterRequest(
                new CompanyRegisterRequest.CompanyInfo(name, regNo, "대표", "서울", "02-000-0000", "c" + n + "@test.example"),
                new CompanyRegisterRequest.AdminInfo("관리자", adminEmail, PASSWORD)));
        long rootOrgUnitId = jdbc.queryForObject(
                "SELECT id FROM org_unit WHERE company_id = ? AND parent_id IS NULL", Long.class, res.companyId());
        return new Company(res.companyId(), res.employeeId(), adminEmail, rootOrgUnitId);
    }

    /**
     * 아직 아무 회사도 쓰지 않은 사업자등록번호(숫자 10자리). 회사 정보 수정처럼 테스트가 번호를 직접 넣어야 할 때 쓴다 —
     * 회사 ID 등으로 번호를 만들면 지우지 않고 남겨 둔 다른 회사의 번호와 겹칠 수 있다.
     */
    public String businessRegNo() {
        return regNo(nextSeq());
    }

    private static String regNo(long n) {
        return "%010d".formatted(n % 10_000_000_000L);
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
        long n = nextSeq();
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

    private long nextSeq() {
        synchronized (SEQ) {
            if (SEQ.get() < 0) {
                // 지난 실행에서 남은 회사·직원의 사업자번호와 test.example 이메일 번호 중 가장 큰 값
                SEQ.set(jdbc.queryForObject("""
                        SELECT GREATEST(
                            (SELECT COALESCE(max(replace(business_reg_no, '-', '')::bigint), 0) FROM company),
                            (SELECT COALESCE(max(substring(email FROM '^[a-z]+([0-9]+)@test\\.example$')::bigint), 0)
                             FROM employee),
                            (SELECT COALESCE(max(substring(email FROM '^[a-z]+([0-9]+)@test\\.example$')::bigint), 0)
                             FROM company))
                        """, Long.class));
            }
            return SEQ.incrementAndGet();
        }
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
