package com.nexuslabs.hr.domain.payroll.repository;

import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayItemRepository extends JpaRepository<PayItem, Long> {
}
