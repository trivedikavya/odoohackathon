package com.urbanfurniture.accounting.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    Optional<Journal> findByBookIdAndType(Long bookId, JournalType type);

    List<Journal> findByBookIdOrderByCodeAsc(Long bookId);
}
