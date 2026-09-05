package com.urbanfurniture.accounting.analytic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @Query("""
            select b from Budget b
            join fetch b.analyticAccount a
            where b.book.id = :bookId
              and (:includeArchived = true or b.active = true)
            order by b.periodStart desc, b.id desc
            """)
    List<Budget> findForBook(@Param("bookId") Long bookId,
                             @Param("includeArchived") boolean includeArchived);
}
