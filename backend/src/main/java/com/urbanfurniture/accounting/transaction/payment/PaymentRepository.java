package com.urbanfurniture.accounting.transaction.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"contact", "invoice", "bill"})
    Optional<Payment> findWithDetailsById(Long id);

    @Query("""
            select p from Payment p
            where (cast(:contactScope as string) is null or p.contact.id = :contactScope)
              and (cast(:direction as string) is null or p.direction = :direction)
              and (lower(p.paymentNo) like lower(concat('%', :search, '%'))
                   or lower(p.contact.name) like lower(concat('%', :search, '%')))
            order by p.id desc
            """)
    Page<Payment> search(@Param("search") String search,
                         @Param("direction") PaymentDirection direction,
                         @Param("contactScope") Long contactScope,
                         Pageable pageable);

    List<Payment> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    List<Payment> findByBillIdOrderByIdAsc(Long billId);
}
