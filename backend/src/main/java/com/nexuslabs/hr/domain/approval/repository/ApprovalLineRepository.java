package com.nexuslabs.hr.domain.approval.repository;

import com.nexuslabs.hr.domain.approval.entity.ApprovalLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {
}
