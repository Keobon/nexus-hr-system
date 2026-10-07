package com.nexuslabs.hr.domain.leave.repository;

import com.nexuslabs.hr.domain.leave.entity.LeaveGrant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveGrantRepository extends JpaRepository<LeaveGrant, Long> {
}
