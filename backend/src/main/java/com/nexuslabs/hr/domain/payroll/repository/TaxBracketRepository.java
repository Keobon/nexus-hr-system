package com.nexuslabs.hr.domain.payroll.repository;

import com.nexuslabs.hr.domain.payroll.entity.TaxBracket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxBracketRepository extends JpaRepository<TaxBracket, Long> {
}
