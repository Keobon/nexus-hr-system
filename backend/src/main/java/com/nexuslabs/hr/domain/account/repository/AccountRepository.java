package com.nexuslabs.hr.domain.account.repository;

import com.nexuslabs.hr.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
