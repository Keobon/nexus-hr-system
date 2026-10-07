package com.nexuslabs.hr.domain.account.repository;

import com.nexuslabs.hr.domain.account.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
