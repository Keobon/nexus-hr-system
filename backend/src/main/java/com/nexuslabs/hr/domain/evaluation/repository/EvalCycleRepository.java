package com.nexuslabs.hr.domain.evaluation.repository;

import com.nexuslabs.hr.domain.evaluation.entity.EvalCycle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCycleRepository extends JpaRepository<EvalCycle, Long> {
}
