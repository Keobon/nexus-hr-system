package com.nexuslabs.hr.domain.attendance.repository;

import com.nexuslabs.hr.domain.attendance.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {
}
