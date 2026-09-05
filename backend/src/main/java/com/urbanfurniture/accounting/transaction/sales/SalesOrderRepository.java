package com.urbanfurniture.accounting.transaction.sales;

import com.urbanfurniture.accounting.transaction.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @EntityGraph(attributePaths = {"contact", "lines", "lines.product"})
    Optional<SalesOrder> findWithLinesById(Long id);

    /**
     * {@code contactScope} is supplied by the server from the security context:
     * null for staff, and the bound contact id for portal users.
     */
    @Query("""
            select o from SalesOrder o
            where (cast(:contactScope as string) is null or o.contact.id = :contactScope)
              and (cast(:status as string) is null or o.status = :status)
              and (lower(o.orderNo) like lower(concat('%', :search, '%'))
                   or lower(o.contact.name) like lower(concat('%', :search, '%')))
            order by o.id desc
            """)
    Page<SalesOrder> search(@Param("search") String search,
                            @Param("status") OrderStatus status,
                            @Param("contactScope") Long contactScope,
                            Pageable pageable);

    boolean existsByContactIdAndStatusNot(Long contactId, OrderStatus status);
}
