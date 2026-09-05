package com.urbanfurniture.accounting.master.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findBySystemCode(SystemAccount systemCode);

    Optional<Account> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Account> findAllByOrderByCodeAsc();

    List<Account> findByActiveTrueOrderByCodeAsc();

    List<Account> findByTypeOrderByCodeAsc(AccountType type);
}
