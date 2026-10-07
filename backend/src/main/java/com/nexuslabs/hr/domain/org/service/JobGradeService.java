package com.nexuslabs.hr.domain.org.service;

import com.nexuslabs.hr.domain.org.entity.JobGrade;
import com.nexuslabs.hr.domain.org.repository.JobGradeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 직급 관리(F-ORG-03). 직원 · 발령 이력 · 평가 대상 규칙이 참조하면 사용 중이다. */
@Service
public class JobGradeService extends OrgSettingItemService<JobGrade> {

    public JobGradeService(JobGradeRepository repository, JdbcTemplate jdbc) {
        super(repository, jdbc);
    }

    @Override
    protected JobGrade newItem(String name, int sortOrder) {
        return new JobGrade(name, sortOrder);
    }

    @Override
    protected String usageSql() {
        return """
                SELECT EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND job_grade_id = ?)
                    OR EXISTS (SELECT 1 FROM assignment_history WHERE company_id = ? AND from_job_grade_id = ?)
                    OR EXISTS (SELECT 1 FROM assignment_history WHERE company_id = ? AND to_job_grade_id = ?)
                    OR EXISTS (SELECT 1 FROM eval_cycle_target WHERE company_id = ? AND cond_job_grade_id = ?)
                """;
    }
}
