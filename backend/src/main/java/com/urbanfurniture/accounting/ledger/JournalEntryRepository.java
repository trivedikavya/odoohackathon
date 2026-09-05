package com.urbanfurniture.accounting.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @EntityGraph(attributePaths = {"journal", "lines", "lines.account", "lines.contact"})
    Optional<JournalEntry> findWithLinesById(Long id);

    /**
     * {@code from}/{@code to} are resolved by the caller. The optional
     * {@code sourceType} is wrapped in a cast so PostgreSQL can determine the
     * parameter type in the {@code is null} branch.
     */
    @Query("""
            select e from JournalEntry e
            where e.entryDate >= :from
              and e.entryDate <= :to
              and (cast(:sourceType as string) is null or e.sourceType = :sourceType)
            order by e.entryDate desc, e.id desc
            """)
    Page<JournalEntry> search(@Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("sourceType") SourceType sourceType,
                              Pageable pageable);

    List<JournalEntry> findBySourceTypeAndSourceId(SourceType sourceType, Long sourceId);
}
