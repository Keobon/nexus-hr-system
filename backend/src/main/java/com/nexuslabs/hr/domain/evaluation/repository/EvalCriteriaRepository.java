package com.nexuslabs.hr.domain.evaluation.repository;

import com.nexuslabs.hr.domain.evaluation.entity.EvalCriteria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCriteriaRepository extends JpaRepository<EvalCriteria, Long> {
}
