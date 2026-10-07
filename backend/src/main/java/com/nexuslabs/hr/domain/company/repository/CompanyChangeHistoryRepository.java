package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.CompanyChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyChangeHistoryRepository extends JpaRepository<CompanyChangeHistory, Long> {
}
