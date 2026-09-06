package com.urbanfurniture.accounting.master;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Matches a counterparty's item name against this book's own catalogue. */
    Optional<Product> findFirstByBookIdAndNameIgnoreCase(Long bookId, String name);

    /** The sellable catalogue, for the buyer-facing pickers. */
    @EntityGraph(attributePaths = {"components", "components.component"})
    List<Product> findByBookIdAndActiveTrueOrderByNameAsc(Long bookId);

    /**
     * Takes a write lock on one product row.
     * <p>
     * Used before costing an issue, because working out the weighted
     * average is a read-then-write: two concurrent sales of the same item
     * would otherwise both read the same quantity and both pass the
     * availability check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"components", "components.component"})
    @Query("""
            select p from Product p
            where p.book.id = :bookId
              and (:includeArchived = true or p.active = true)
              and (lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.category, '')) like lower(concat('%', :search, '%')))
            order by p.name asc
            """)
    List<Product> search(@Param("bookId") Long bookId,
                         @Param("search") String search,
                         @Param("includeArchived") boolean includeArchived);
}
