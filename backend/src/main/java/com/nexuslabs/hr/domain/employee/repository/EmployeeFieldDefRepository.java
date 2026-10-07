package com.nexuslabs.hr.domain.employee.repository;

import com.nexuslabs.hr.domain.employee.entity.EmployeeFieldDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeFieldDefRepository extends JpaRepository<EmployeeFieldDef, Long> {

    List<EmployeeFieldDef> findByActiveTrueOrderBySortOrderAscIdAsc();
}
