package com.nexuslabs.hr.domain.attendance.repository;

import com.nexuslabs.hr.domain.attendance.entity.ExpenseClaimLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseClaimLineRepository extends JpaRepository<ExpenseClaimLine, Long> {
}
