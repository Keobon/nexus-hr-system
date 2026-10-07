package com.nexuslabs.hr.domain.approval.repository;

import com.nexuslabs.hr.domain.approval.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {
}
