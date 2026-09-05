package com.urbanfurniture.accounting.analytic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalyticAccountRepository extends JpaRepository<AnalyticAccount, Long> {

    boolean existsByBookIdAndCodeIgnoreCase(Long bookId, String code);

    @Query("""
            select a from AnalyticAccount a
            where a.book.id = :bookId
              and (:includeArchived = true or a.active = true)
            order by a.code asc
            """)
    List<AnalyticAccount> findForBook(@Param("bookId") Long bookId,
                                      @Param("includeArchived") boolean includeArchived);
}
