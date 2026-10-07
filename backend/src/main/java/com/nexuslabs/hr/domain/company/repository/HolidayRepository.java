package com.nexuslabs.hr.domain.company.repository;

import com.nexuslabs.hr.domain.company.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
}
