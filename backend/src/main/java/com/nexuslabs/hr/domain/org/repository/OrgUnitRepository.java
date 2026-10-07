package com.nexuslabs.hr.domain.org.repository;

import com.nexuslabs.hr.domain.org.entity.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    boolean existsByParentIdAndName(Long parentId, String name);

    boolean existsByParentIdAndNameAndIdNot(Long parentId, String name, Long id);

    @Query("SELECT COALESCE(max(o.sortOrder), 0) FROM OrgUnit o WHERE o.parent.id = :parentId")
    int maxSortOrderUnder(Long parentId);
}
