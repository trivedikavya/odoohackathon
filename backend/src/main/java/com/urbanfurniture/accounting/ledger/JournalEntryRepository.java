package com.urbanfurniture.accounting.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @EntityGraph(attributePaths = {"journal", "lines", "lines.account", "lines.contact", "lines.contact.party"})
    Optional<JournalEntry> findWithLinesById(Long id);

    /**
     * Nullable filters use {@code cast(... as string) is null} because
     * PostgreSQL cannot infer a type for a bare null bind in this position.
     */
    @Query("""
            select e from JournalEntry e
            where e.book.id = :bookId
              and (cast(:sourceType as string) is null or e.sourceType = :sourceType)
              and e.entryDate >= :from and e.entryDate <= :to
            order by e.entryDate desc, e.id desc
            """)
    Page<JournalEntry> search(@Param("bookId") Long bookId,
                              @Param("sourceType") SourceType sourceType,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              Pageable pageable);
}
