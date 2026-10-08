package com.nexuslabs.hr.domain.employee.repository;

import com.nexuslabs.hr.domain.employee.entity.EmployeeDocType;
import com.nexuslabs.hr.domain.employee.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    List<EmployeeDocument> findByEmployee_IdAndDocType(Long employeeId, EmployeeDocType docType);
}
