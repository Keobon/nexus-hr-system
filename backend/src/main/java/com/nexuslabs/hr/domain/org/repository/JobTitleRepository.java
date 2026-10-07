package com.nexuslabs.hr.domain.org.repository;

import com.nexuslabs.hr.domain.org.entity.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {
}
