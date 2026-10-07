package com.nexuslabs.hr.domain.employee.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 사원번호 자동 채번(BR-EMP-006) — 접두어 + 입사연도 + "-" + 4자리 순번. 예: NX-2026-0001, 접두어가 없으면 2026-0001.
 * 순번은 회사·입사연도마다 1부터. 직접 입력한 번호와 겹치면 다음 번호로 건너뛴다.
 * employee_no_seq 행을 잠그고 올리므로 같은 트랜잭션 안에서 직원을 저장해야 한다.
 */
@Component
public class EmployeeNoGenerator {

    private final JdbcTemplate jdbc;

    public EmployeeNoGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next(long companyId, int hireYear) {
        String prefix = jdbc.queryForObject(
                "SELECT COALESCE(employee_no_prefix, '') FROM company WHERE id = ?", String.class, companyId);
        while (true) {
            Integer seq = jdbc.queryForObject("""
                            INSERT INTO employee_no_seq (company_id, hire_year, last_seq) VALUES (?, ?, 1)
                            ON CONFLICT (company_id, hire_year) DO UPDATE SET last_seq = employee_no_seq.last_seq + 1
                            RETURNING last_seq
                            """,
                    Integer.class, companyId, hireYear);
            String candidate = "%s%d-%04d".formatted(prefix, hireYear, seq);
            Boolean taken = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND employee_no = ?)",
                    Boolean.class, companyId, candidate);
            if (!Boolean.TRUE.equals(taken)) {
                return candidate;
            }
        }
    }
}
