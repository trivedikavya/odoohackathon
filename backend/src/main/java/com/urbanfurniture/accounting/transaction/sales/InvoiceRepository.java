package com.urbanfurniture.accounting.transaction.sales;

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

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"contact", "lines", "lines.product"})
    Optional<Invoice> findWithLinesById(Long id);

    /**
     * Row-level scoped listing. {@code contactScope} comes from the JWT
     * principal, never from the request, so a portal user cannot list another
     * contact's invoices by manipulating parameters.
     */
    @Query("""
            select i from Invoice i
            where (cast(:contactScope as string) is null or i.contact.id = :contactScope)
              and (cast(:status as string) is null or i.status = :status)
              and (lower(i.invoiceNo) like lower(concat('%', :search, '%'))
                   or lower(i.contact.name) like lower(concat('%', :search, '%')))
            order by i.id desc
            """)
    Page<Invoice> search(@Param("search") String search,
                         @Param("status") DocumentStatus status,
                         @Param("contactScope") Long contactScope,
                         Pageable pageable);

    boolean existsBySalesOrderIdAndStatusNot(Long salesOrderId, DocumentStatus status);

    List<Invoice> findBySalesOrderId(Long salesOrderId);

    @Query("""
            select coalesce(sum(i.totalAmount), 0) from Invoice i
            where i.status <> com.urbanfurniture.accounting.transaction.DocumentStatus.CANCELLED
              and i.status <> com.urbanfurniture.accounting.transaction.DocumentStatus.DRAFT
            """)
    BigDecimal totalPostedSales();

    @Query("""
            select i from Invoice i
            where i.status in (com.urbanfurniture.accounting.transaction.DocumentStatus.POSTED,
                               com.urbanfurniture.accounting.transaction.DocumentStatus.PARTIALLY_PAID)
              and (cast(:contactScope as string) is null or i.contact.id = :contactScope)
            order by i.dueDate asc nulls last, i.id asc
            """)
    List<Invoice> findOutstanding(@Param("contactScope") Long contactScope);
}
