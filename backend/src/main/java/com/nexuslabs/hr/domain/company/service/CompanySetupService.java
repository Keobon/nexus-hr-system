package com.nexuslabs.hr.domain.company.service;

import com.nexuslabs.hr.domain.company.dto.SetupStatusResponse;
import com.nexuslabs.hr.domain.company.dto.SetupStatusResponse.Step;
import com.nexuslabs.hr.domain.company.dto.SetupStep;
import com.nexuslabs.hr.domain.company.entity.Company;
import com.nexuslabs.hr.domain.company.repository.CompanyRepository;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 초기 설정 마법사의 상태와 완료 처리(F-COMP-03). 각 단계는 해당 기능의 API 를 그대로 쓰고, 여기서는 진행 상황만 알려준다.
 * 여러 영역의 개수를 세는 조회라 JDBC 로 하고, 모든 하위 쿼리에 company_id 조건을 넣는다.
 */
@Service
public class CompanySetupService {

    private final CompanyRepository companyRepository;
    private final JdbcTemplate jdbc;

    public CompanySetupService(CompanyRepository companyRepository, JdbcTemplate jdbc) {
        this.companyRepository = companyRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SetupStatusResponse status(LoginUser user) {
        return status(company(user));
    }

    /** 초기 설정 완료. 이미 완료했으면 그대로 둔다. 모든 단계를 건너뛰어도 완료할 수 있다. */
    @Transactional
    public SetupStatusResponse complete(LoginUser user) {
        Company company = company(user);
        company.completeSetup();
        return status(company);
    }

    private SetupStatusResponse status(Company company) {
        long id = company.getId();
        Map<String, Object> c = jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM org_unit WHERE company_id = ? AND parent_id IS NOT NULL AND is_active) AS org_units,
                       (SELECT count(*) FROM job_grade WHERE company_id = ? AND is_active) AS job_grades,
                       (SELECT count(*) FROM job_title WHERE company_id = ? AND is_active) AS job_titles,
                       (SELECT count(*) FROM employment_type WHERE company_id = ? AND is_active) AS employment_types,
                       (SELECT count(*) FROM work_schedule WHERE company_id = ?) AS work_schedules,
                       (SELECT count(*) FROM holiday WHERE company_id = ?) AS holidays,
                       (SELECT count(*) FROM leave_type WHERE company_id = ? AND is_active) AS leave_types,
                       (SELECT count(*) FROM pay_item WHERE company_id = ? AND is_active) AS pay_items,
                       (SELECT count(*) FROM employee WHERE company_id = ? AND status <> 'RESIGNED') AS employees
                """, id, id, id, id, id, id, id, id, id);
        int orgUnits = count(c, "org_units");
        int jobGrades = count(c, "job_grades");
        int employmentTypes = count(c, "employment_types");
        int workSchedules = count(c, "work_schedules");
        int leaveTypes = count(c, "leave_types");
        int payItems = count(c, "pay_items");
        int employees = count(c, "employees");

        // 최상위 조직과 회사를 등록한 관리자는 처음부터 있으므로 세지 않는다
        return new SetupStatusResponse(company.isSetupCompleted(), List.of(
                new Step(SetupStep.ORG_UNITS, counts("orgUnits", orgUnits), orgUnits > 0),
                new Step(SetupStep.JOB_GRADES_TITLES, counts("jobGrades", jobGrades, "jobTitles", count(c, "job_titles")),
                        jobGrades > 0),
                new Step(SetupStep.EMPLOYMENT_TYPES, counts("employmentTypes", employmentTypes), employmentTypes > 0),
                new Step(SetupStep.WORK_SCHEDULE_HOLIDAYS,
                        counts("workSchedules", workSchedules, "holidays", count(c, "holidays")), workSchedules > 0),
                new Step(SetupStep.LEAVE_TYPES, counts("leaveTypes", leaveTypes), leaveTypes > 0),
                new Step(SetupStep.PAY_ITEMS, counts("payItems", payItems), payItems > 0),
                new Step(SetupStep.EMPLOYEES, counts("employees", employees), employees > 1)));
    }

    private Company company(LoginUser user) {
        return companyRepository.findById(user.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static int count(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    private static Map<String, Integer> counts(String name, int value) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(name, value);
        return counts;
    }

    private static Map<String, Integer> counts(String name1, int value1, String name2, int value2) {
        Map<String, Integer> counts = counts(name1, value1);
        counts.put(name2, value2);
        return counts;
    }
}
