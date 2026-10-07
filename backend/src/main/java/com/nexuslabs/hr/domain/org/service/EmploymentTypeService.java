package com.nexuslabs.hr.domain.org.service;

import com.nexuslabs.hr.domain.org.entity.EmploymentType;
import com.nexuslabs.hr.domain.org.repository.EmploymentTypeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 고용형태 관리(F-ORG-04). 직원 · 평가 대상 규칙이 참조하면 사용 중이다. */
@Service
public class EmploymentTypeService extends OrgSettingItemService<EmploymentType> {

    public EmploymentTypeService(EmploymentTypeRepository repository, JdbcTemplate jdbc) {
        super(repository, jdbc);
    }

    @Override
    protected EmploymentType newItem(String name, int sortOrder) {
        return new EmploymentType(name, sortOrder);
    }

    @Override
    protected String usageSql() {
        return """
                SELECT EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND employment_type_id = ?)
                    OR EXISTS (SELECT 1 FROM eval_cycle_target WHERE company_id = ? AND cond_employment_type_id = ?)
                """;
    }
}
