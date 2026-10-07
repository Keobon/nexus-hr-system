package com.nexuslabs.hr.domain.attendance.repository;

import com.nexuslabs.hr.domain.attendance.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {
}
