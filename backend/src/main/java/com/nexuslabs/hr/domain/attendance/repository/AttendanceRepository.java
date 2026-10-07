package com.nexuslabs.hr.domain.attendance.repository;

import com.nexuslabs.hr.domain.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
}
