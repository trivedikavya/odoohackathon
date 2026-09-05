package com.urbanfurniture.accounting.transaction.purchase;

import com.urbanfurniture.accounting.transaction.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = {"contact", "lines", "lines.product"})
    Optional<PurchaseOrder> findWithLinesById(Long id);

    @Query("""
            select o from PurchaseOrder o
            where (cast(:contactScope as string) is null or o.contact.id = :contactScope)
              and (cast(:status as string) is null or o.status = :status)
              and (lower(o.orderNo) like lower(concat('%', :search, '%'))
                   or lower(o.contact.name) like lower(concat('%', :search, '%')))
            order by o.id desc
            """)
    Page<PurchaseOrder> search(@Param("search") String search,
                               @Param("status") OrderStatus status,
                               @Param("contactScope") Long contactScope,
                               Pageable pageable);
}
