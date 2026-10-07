package com.nexuslabs.hr.domain.attendance.repository;

import com.nexuslabs.hr.domain.attendance.entity.BusinessTrip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessTripRepository extends JpaRepository<BusinessTrip, Long> {
}
