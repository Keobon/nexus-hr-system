package com.nexuslabs.hr.domain.leave.repository;

import com.nexuslabs.hr.domain.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
}
