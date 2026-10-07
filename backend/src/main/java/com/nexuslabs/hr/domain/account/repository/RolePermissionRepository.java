package com.nexuslabs.hr.domain.account.repository;

import com.nexuslabs.hr.domain.account.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
}
