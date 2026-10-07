package com.nexuslabs.hr.domain.approval.repository;

import com.nexuslabs.hr.domain.approval.entity.ApprovalLineStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineStepRepository extends JpaRepository<ApprovalLineStep, Long> {
}
