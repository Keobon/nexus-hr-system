package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.CompanyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, Long> {
}
