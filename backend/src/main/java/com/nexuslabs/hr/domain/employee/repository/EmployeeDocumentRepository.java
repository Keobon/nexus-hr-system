package com.nexuslabs.hr.domain.employee.repository;

import com.nexuslabs.hr.domain.employee.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
}
