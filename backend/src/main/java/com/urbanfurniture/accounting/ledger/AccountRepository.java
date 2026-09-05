package com.urbanfurniture.accounting.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByBookIdAndSystemCode(Long bookId, SystemAccount systemCode);

    boolean existsByBookIdAndCodeIgnoreCase(Long bookId, String code);

    @Query("""
            select a from Account a
            where a.book.id = :bookId
              and (:includeArchived = true or a.active = true)
            order by a.code asc
            """)
    List<Account> findForBook(@Param("bookId") Long bookId,
                              @Param("includeArchived") boolean includeArchived);
}
