package com.nexuslabs.hr.domain.evaluation.repository;

import com.nexuslabs.hr.domain.evaluation.entity.EvalCycleTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalCycleTargetRepository extends JpaRepository<EvalCycleTarget, Long> {

    List<EvalCycleTarget> findByEvalCycleId(long evalCycleId);
}
