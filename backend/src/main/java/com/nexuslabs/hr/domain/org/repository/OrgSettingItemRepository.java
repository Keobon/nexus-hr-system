package com.nexuslabs.hr.domain.org.repository;

import com.nexuslabs.hr.domain.org.entity.OrgSettingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/** 직급 · 직책 · 고용형태 Repository 의 공통 조회. */
@NoRepositoryBean
public interface OrgSettingItemRepository<T extends OrgSettingItem> extends JpaRepository<T, Long> {

    List<T> findAllByOrderBySortOrderAscIdAsc();

    List<T> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
