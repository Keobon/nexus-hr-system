package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
}
