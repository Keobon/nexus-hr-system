package com.nexuslabs.hr.domain.payroll.repository;

import com.nexuslabs.hr.domain.payroll.entity.Paystub;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaystubRepository extends JpaRepository<Paystub, Long> {
}
