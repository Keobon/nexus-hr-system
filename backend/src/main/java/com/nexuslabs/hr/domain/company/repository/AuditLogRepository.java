package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
