package com.urbanfurniture.accounting.master;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

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
