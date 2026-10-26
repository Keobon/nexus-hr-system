package com.nexuslabs.hr.domain.attendance.repository;

import com.nexuslabs.hr.domain.attendance.entity.AttendanceExplanation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceExplanationRepository extends JpaRepository<AttendanceExplanation, Long> {
}
