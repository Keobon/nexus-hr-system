package com.nexuslabs.hr.domain.payroll.repository;

import com.nexuslabs.hr.domain.payroll.entity.PayVariable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayVariableRepository extends JpaRepository<PayVariable, Long> {
}
