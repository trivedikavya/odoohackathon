package com.urbanfurniture.accounting.master.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where (:includeArchived = true or p.active = true)
              and (lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.category, '')) like lower(concat('%', :search, '%')))
              and (cast(:type as string) is null or p.type = :type)
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("type") ProductType type,
                         @Param("includeArchived") boolean includeArchived,
                         Pageable pageable);

    List<Product> findByActiveTrueOrderByNameAsc();

    @Query("select distinct p.category from Product p where p.category is not null order by p.category")
    List<String> findDistinctCategories();
}
