package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    List<WorkSchedule> findAllByOrderByEffectiveFromDesc();

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);
}
