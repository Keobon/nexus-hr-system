package com.nexuslabs.hr.domain.payroll.repository;

import com.nexuslabs.hr.domain.payroll.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
}
