package com.nexuslabs.hr.domain.assignment.repository;

import com.nexuslabs.hr.domain.assignment.entity.AssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {
}
