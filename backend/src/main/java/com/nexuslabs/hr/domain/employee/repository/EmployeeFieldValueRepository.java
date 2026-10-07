package com.nexuslabs.hr.domain.employee.repository;

import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeFieldValueRepository extends JpaRepository<EmployeeFieldValue, Long> {
}
