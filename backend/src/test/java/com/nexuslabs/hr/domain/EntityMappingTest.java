package com.nexuslabs.hr.domain;

import com.nexuslabs.hr.domain.company.entity.AuditLog;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldDef;
import com.nexuslabs.hr.domain.employee.entity.FieldType;
import com.nexuslabs.hr.domain.leave.entity.LeaveType;
import com.nexuslabs.hr.domain.org.entity.EmploymentType;
import com.nexuslabs.hr.domain.org.entity.OrgUnit;
import com.nexuslabs.hr.domain.payroll.entity.PayrollRun;
import com.nexuslabs.hr.domain.payroll.entity.PayrollStatus;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 엔티티 매핑 확인(B-02). 부팅 때의 validate 는 컬럼 타입만 보므로, ENUM · JSONB · SMALLINT · CHAR 컬럼을 실제로 저장하고 다시 읽는다.
 * 트랜잭션이 열리기 전에 회사를 정해야 해서 @Transactional 대신 TransactionTemplate 을 쓰고 끝에 롤백한다.
 */
@SpringBootTest
class EntityMappingTest {

    static final long COMPANY_ID = 990_001L;

    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void 저장한_값을_그대로_다시_읽는다() {
        TenantContext.set(COMPANY_ID);
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("""
                    INSERT INTO company (id, name, business_reg_no, ceo_name, address, phone, email)
                    VALUES (?, '매핑테스트', '999-00-00001', '대표', '주소', '02-0000-0000', 'mapping@test.example')
                    """, COMPANY_ID);

            OrgUnit root = new OrgUnit(null, "매핑테스트", null, 0);
            EmploymentType regular = new EmploymentType("정규직", 1);
            em.persist(root);
            em.persist(regular);
            Employee employee = new Employee("T-0001", "홍길동", "mapping-emp@test.example", LocalDate.of(2026, 1, 2),
                    root, null, null, regular);
            em.persist(employee);

            EmployeeFieldDef fieldDef = new EmployeeFieldDef("기술 스택", FieldType.SELECT, List.of("Java", "SQL"),
                    false, true, true, 1);
            LeaveType annual = new LeaveType("연차", (short) 15, true, true, true,
                    (short) 3, (short) 2, (short) 1, (short) 25, 1);
            PayrollRun run = new PayrollRun("2026-10", LocalDate.of(2026, 10, 25), "매핑테스트", "대표", "999-00-00001",
                    "주소", employee.getId(), OffsetDateTime.now());
            AuditLog log = new AuditLog(employee.getId(), AuditAction.UPDATE, "EMPLOYEE", employee.getId(),
                    Map.of("name", "전"), Map.of("name", "후", "count", 2));
            em.persist(fieldDef);
            em.persist(annual);
            em.persist(run);
            em.persist(log);
            em.flush();
            em.clear();

            Employee foundEmployee = em.find(Employee.class, employee.getId());
            assertThat(foundEmployee.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(foundEmployee.getStatus()).isEqualTo(EmpStatus.ACTIVE);
            assertThat(foundEmployee.getOrgUnit().getName()).isEqualTo("매핑테스트");
            assertThat(foundEmployee.getCreatedAt()).isNotNull();

            EmployeeFieldDef foundDef = em.find(EmployeeFieldDef.class, fieldDef.getId());
            assertThat(foundDef.getFieldType()).isEqualTo(FieldType.SELECT);
            assertThat(foundDef.getOptions()).containsExactly("Java", "SQL");

            LeaveType foundType = em.find(LeaveType.class, annual.getId());
            assertThat(foundType.getAnnualDays()).isEqualTo((short) 15);
            assertThat(foundType.getSeniorityMaxDays()).isEqualTo((short) 25);

            PayrollRun foundRun = em.find(PayrollRun.class, run.getId());
            assertThat(foundRun.getPayMonth()).isEqualTo("2026-10");
            assertThat(foundRun.getStatus()).isEqualTo(PayrollStatus.CONFIRMED);

            AuditLog foundLog = em.find(AuditLog.class, log.getId());
            assertThat(foundLog.getAction()).isEqualTo(AuditAction.UPDATE);
            assertThat(foundLog.getBeforeValue()).containsEntry("name", "전");
            assertThat(foundLog.getAfterValue()).containsEntry("name", "후").containsEntry("count", 2);
        });
    }
}
