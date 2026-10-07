package com.nexuslabs.hr.domain.payroll.repository;

import com.nexuslabs.hr.domain.payroll.entity.EmployeePayItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeePayItemRepository extends JpaRepository<EmployeePayItem, Long> {
}
