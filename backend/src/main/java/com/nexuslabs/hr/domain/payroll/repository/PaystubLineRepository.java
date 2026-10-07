package com.nexuslabs.hr.domain.payroll.repository;

import com.nexuslabs.hr.domain.payroll.entity.PaystubLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaystubLineRepository extends JpaRepository<PaystubLine, Long> {
}
