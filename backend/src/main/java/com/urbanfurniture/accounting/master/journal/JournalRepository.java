package com.urbanfurniture.accounting.master.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    Optional<Journal> findByCodeIgnoreCase(String code);

    Optional<Journal> findFirstByTypeAndActiveTrueOrderByIdAsc(JournalType type);

    List<Journal> findAllByOrderByIdAsc();

    boolean existsByCodeIgnoreCase(String code);
}
