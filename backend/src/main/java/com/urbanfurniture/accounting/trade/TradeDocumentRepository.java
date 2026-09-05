package com.urbanfurniture.accounting.trade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TradeDocumentRepository extends JpaRepository<TradeDocument, Long> {

    @EntityGraph(attributePaths = {"deal", "deal.buyer", "deal.seller", "book", "contact",
            "contact.party", "lines", "lines.analyticAccount"})
    Optional<TradeDocument> findWithLinesById(Long id);

    Optional<TradeDocument> findByDealIdAndBookIdAndDocType(Long dealId, Long bookId, DocumentType docType);

    List<TradeDocument> findByDealId(Long dealId);

    @Query("""
            select d from TradeDocument d
            where d.book.id = :bookId
              and d.docType = :docType
              and (cast(:status as string) is null or d.status = :status)
              and (lower(d.docNo) like lower(concat('%', :search, '%'))
                   or lower(d.contact.party.name) like lower(concat('%', :search, '%')))
            order by d.id desc
            """)
    Page<TradeDocument> search(@Param("bookId") Long bookId,
                               @Param("docType") DocumentType docType,
                               @Param("status") DocumentStatus status,
                               @Param("search") String search,
                               Pageable pageable);

    /**
     * Everything addressed to one party across every book - the portal
     * view, which is why it is scoped by counterparty rather than by book.
     */
    @Query("""
            select d from TradeDocument d
            where d.contact.party.id = :partyId
              and d.docType in (com.urbanfurniture.accounting.trade.DocumentType.INVOICE,
                                com.urbanfurniture.accounting.trade.DocumentType.BILL)
              and d.status <> com.urbanfurniture.accounting.trade.DocumentStatus.CANCELLED
            order by d.docDate desc, d.id desc
            """)
    Page<TradeDocument> findForCounterparty(@Param("partyId") Long partyId, Pageable pageable);

    /** Issued documents up to a date, for the aging report. */
    @Query("""
            select d from TradeDocument d
            join fetch d.contact c
            join fetch c.party
            where d.book.id = :bookId
              and d.docType = :docType
              and d.status in (com.urbanfurniture.accounting.trade.DocumentStatus.POSTED,
                               com.urbanfurniture.accounting.trade.DocumentStatus.PARTIALLY_PAID)
              and d.docDate <= :asOf
            order by d.dueDate asc nulls last, d.id asc
            """)
    List<TradeDocument> findOpenAsOf(@Param("bookId") Long bookId,
                                     @Param("docType") DocumentType docType,
                                     @Param("asOf") LocalDate asOf);

    @Query("""
            select coalesce(sum(d.totalAmount), 0) from TradeDocument d
            where d.book.id = :bookId and d.docType = :docType
              and d.status <> com.urbanfurniture.accounting.trade.DocumentStatus.CANCELLED
            """)
    java.math.BigDecimal totalPosted(@Param("bookId") Long bookId, @Param("docType") DocumentType docType);

    long countByBookIdAndDocType(Long bookId, DocumentType docType);
}
