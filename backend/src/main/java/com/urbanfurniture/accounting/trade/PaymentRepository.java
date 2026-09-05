package com.urbanfurniture.accounting.trade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            select p from Payment p
            where p.book.id = :bookId
              and lower(p.paymentNo) like lower(concat('%', :search, '%'))
            order by p.id desc
            """)
    Page<Payment> search(@Param("bookId") Long bookId,
                         @Param("search") String search,
                         Pageable pageable);

    List<Payment> findByDocumentIdOrderByIdAsc(Long documentId);

    /** The portal view: payments against documents addressed to one party. */
    @Query("""
            select p from Payment p
            where p.document.contact.party.id = :partyId
            order by p.id desc
            """)
    Page<Payment> findForCounterparty(@Param("partyId") Long partyId, Pageable pageable);
}
