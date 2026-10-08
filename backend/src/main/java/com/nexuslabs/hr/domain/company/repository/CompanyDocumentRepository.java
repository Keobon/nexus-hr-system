package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.CompanyDocType;
import com.nexuslabs.hr.domain.company.entity.CompanyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, Long> {

    List<CompanyDocument> findByDocType(CompanyDocType docType);
}
