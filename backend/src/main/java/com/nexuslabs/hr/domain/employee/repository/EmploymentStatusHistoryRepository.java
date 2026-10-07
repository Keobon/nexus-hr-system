package com.nexuslabs.hr.domain.employee.repository;

import com.nexuslabs.hr.domain.employee.entity.EmploymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentStatusHistoryRepository extends JpaRepository<EmploymentStatusHistory, Long> {
}
