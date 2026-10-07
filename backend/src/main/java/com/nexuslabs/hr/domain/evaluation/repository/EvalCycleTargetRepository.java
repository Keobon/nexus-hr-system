package com.nexuslabs.hr.domain.evaluation.repository;

import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleTarget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCycleTargetRepository extends JpaRepository<EvalCycleTarget, Long> {
}
