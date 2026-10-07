package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
