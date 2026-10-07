package com.nexuslabs.hr.domain.leave.repository;

import com.nexuslabs.hr.domain.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
}
