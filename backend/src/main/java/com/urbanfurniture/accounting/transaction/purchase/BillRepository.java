package com.urbanfurniture.accounting.transaction.purchase;

import com.urbanfurniture.accounting.transaction.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    @EntityGraph(attributePaths = {"contact", "lines", "lines.product"})
    Optional<Bill> findWithLinesById(Long id);

    @Query("""
            select b from Bill b
            where (cast(:contactScope as string) is null or b.contact.id = :contactScope)
              and (cast(:status as string) is null or b.status = :status)
              and (lower(b.billNo) like lower(concat('%', :search, '%'))
                   or lower(b.contact.name) like lower(concat('%', :search, '%')))
            order by b.id desc
            """)
    Page<Bill> search(@Param("search") String search,
                      @Param("status") DocumentStatus status,
                      @Param("contactScope") Long contactScope,
                      Pageable pageable);

    List<Bill> findByPurchaseOrderId(Long purchaseOrderId);

    @Query("""
            select coalesce(sum(b.totalAmount), 0) from Bill b
            where b.status <> com.urbanfurniture.accounting.transaction.DocumentStatus.CANCELLED
              and b.status <> com.urbanfurniture.accounting.transaction.DocumentStatus.DRAFT
            """)
    BigDecimal totalPostedPurchases();

    @Query("""
            select b from Bill b
            where b.status in (com.urbanfurniture.accounting.transaction.DocumentStatus.POSTED,
                               com.urbanfurniture.accounting.transaction.DocumentStatus.PARTIALLY_PAID)
              and (cast(:contactScope as string) is null or b.contact.id = :contactScope)
            order by b.dueDate asc nulls last, b.id asc
            """)
    List<Bill> findOutstanding(@Param("contactScope") Long contactScope);
}
