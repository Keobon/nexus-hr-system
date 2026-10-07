package com.nexuslabs.hr.domain.evaluation.repository;

import com.nexuslabs.hr.domain.evaluation.entity.EvalQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalQuestionRepository extends JpaRepository<EvalQuestion, Long> {
}
