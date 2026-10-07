package com.nexuslabs.hr.domain.org.service;

import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.domain.org.repository.JobTitleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 직책 관리(F-ORG-03). 직원 · 발령 이력 · 승인선 조건 · 승인선 단계 · 평가 대상 규칙이 참조하면 사용 중이다. */
@Service
public class JobTitleService extends OrgSettingItemService<JobTitle> {

    public JobTitleService(JobTitleRepository repository, JdbcTemplate jdbc) {
        super(repository, jdbc);
    }

    @Override
    protected JobTitle newItem(String name, int sortOrder) {
        return new JobTitle(name, sortOrder);
    }

    @Override
    protected String usageSql() {
        return """
                SELECT EXISTS (SELECT 1 FROM employee WHERE company_id = ? AND job_title_id = ?)
                    OR EXISTS (SELECT 1 FROM assignment_history WHERE company_id = ? AND from_job_title_id = ?)
                    OR EXISTS (SELECT 1 FROM assignment_history WHERE company_id = ? AND to_job_title_id = ?)
                    OR EXISTS (SELECT 1 FROM approval_line WHERE company_id = ? AND cond_job_title_id = ?)
                    OR EXISTS (SELECT 1 FROM approval_line_step WHERE company_id = ? AND job_title_id = ?)
                    OR EXISTS (SELECT 1 FROM eval_cycle_target WHERE company_id = ? AND cond_job_title_id = ?)
                """;
    }
}
