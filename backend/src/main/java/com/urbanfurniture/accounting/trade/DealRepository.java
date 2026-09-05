package com.urbanfurniture.accounting.trade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @EntityGraph(attributePaths = {"buyer", "seller", "initiatedBy", "lines"})
    Optional<Deal> findWithLinesById(Long id);

    /**
     * Every deal a party is on either side of. Drafts are visible only to
     * whoever raised them - an unsent request is not yet a proposal.
     */
    @Query("""
            select d from Deal d
            where (d.buyer.id = :partyId or d.seller.id = :partyId)
              and (d.status <> com.urbanfurniture.accounting.trade.DealStatus.RFQ_DRAFT
                   or d.initiatedBy.id = :partyId)
              and (cast(:status as string) is null or d.status = :status)
              and lower(d.dealNo) like lower(concat('%', :search, '%'))
            order by d.id desc
            """)
    Page<Deal> searchForParty(@Param("partyId") Long partyId,
                              @Param("status") DealStatus status,
                              @Param("search") String search,
                              Pageable pageable);

    /** Requests awaiting this party''s decision - the inbox. */
    @Query("""
            select d from Deal d
            where d.status = com.urbanfurniture.accounting.trade.DealStatus.RFQ_SENT
              and d.initiatedBy.id <> :partyId
              and (d.buyer.id = :partyId or d.seller.id = :partyId)
            order by d.id desc
            """)
    Page<Deal> inboxForParty(@Param("partyId") Long partyId, Pageable pageable);
}
