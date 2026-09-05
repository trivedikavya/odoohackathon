package com.urbanfurniture.accounting.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLoginIdIgnoreCase(String loginId);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByLoginIdIgnoreCase(String loginId);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByPartyIdOrderByIdAsc(Long partyId);
}
