package com.nexuslabs.hr.domain.evaluation.repository;

import com.nexuslabs.hr.domain.evaluation.entity.EvalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalTemplateRepository extends JpaRepository<EvalTemplate, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
