package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.PlatformSequenceService;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.identity.PartyRepository;
import com.urbanfurniture.accounting.security.AppUserPrincipal;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.tax.GstSplit;
import com.urbanfurniture.accounting.tax.TaxCalculator;
import com.urbanfurniture.accounting.tax.TaxTreatment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The request-for-quotation workflow: raise, send, accept or reject,
 * deliver.
 * <p>
 * None of it touches the ledger. An order is a commitment; money only
 * moves when the resulting invoice is posted, which is
 * {@link DocumentService}'s job.
 */
@Service
@RequiredArgsConstructor
public class DealService {

    private final DealRepository dealRepository;
    private final PartyRepository partyRepository;
    private final TradeDocumentRepository documentRepository;
    private final PlatformSequenceService platformSequenceService;
    private final DocumentService documentService;
    private final CurrentUser currentUser;
    private final TradeMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<TradeDtos.DealResponse> search(DealStatus status, String search, Pageable pageable) {
        Long partyId = currentUser.requirePartyId();
        return PageResponse.of(
                dealRepository.searchForParty(partyId, status, SearchTerms.normalize(search), pageable),
                d -> mapper.toDealResponse(d, partyId));
    }

    /** Requests awaiting this party's decision. */
    @Transactional(readOnly = true)
    public PageResponse<TradeDtos.DealResponse> inbox(Pageable pageable) {
        Long partyId = currentUser.requirePartyId();
        return PageResponse.of(dealRepository.inboxForParty(partyId, pageable),
                d -> mapper.toDealResponse(d, partyId));
    }

    @Transactional(readOnly = true)
    public TradeDtos.DealResponse get(Long id) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = requireDeal(id);
        assertInvolved(deal, partyId);
        return mapper.toDealResponse(deal, partyId);
    }

    /**
     * Raise a request for quotation. The caller is the buyer.
     * <p>
     * Prices and tax are captured now, but they are a <em>proposal</em>:
     * the supplier accepts or rejects them, and until they accept nothing
     * about this row is binding on either side.
     */
    @Transactional
    public TradeDtos.DealResponse create(TradeDtos.CreateDealRequest request) {
        AppUserPrincipal me = currentUser.require();
        Party buyer = partyRepository.findById(me.getPartyId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Party", me.getPartyId()));
        Party seller = partyRepository.findById(request.sellerPartyId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Party", request.sellerPartyId()));

        if (buyer.getId().equals(seller.getId())) {
            throw new ApiExceptions.BusinessRuleException("You cannot raise a request on yourself");
        }
        if (!seller.getType().canSell()) {
            throw new ApiExceptions.BusinessRuleException(
                    "'" + seller.getName() + "' is a customer and cannot supply goods");
        }

        // Frozen now: the tax treatment must reflect where the goods were
        // supplied at the time of the deal, not wherever the buyer moves to.
        String placeOfSupply = buyer.getState();
        TaxTreatment treatment = TaxCalculator.treatmentFor(seller.getState(), placeOfSupply);

        Deal deal = Deal.builder()
                .dealNo(platformSequenceService.next(PlatformSequenceService.DEAL))
                .buyer(buyer)
                .seller(seller)
                .initiatedBy(buyer)
                .status(DealStatus.RFQ_DRAFT)
                .dealDate(request.dealDate())
                .expectedDelivery(request.expectedDelivery())
                .placeOfSupply(placeOfSupply)
                .notes(request.notes())
                .build();

        for (TradeDtos.DealLineRequest lr : request.lines()) {
            BigDecimal rate = lr.taxRate() == null ? BigDecimal.ZERO : lr.taxRate();
            LineAmounts amounts = LineAmounts.compute(lr.quantity(), lr.unitPrice(), rate);
            GstSplit split = TaxCalculator.split(amounts.tax(), treatment);

            deal.addLine(DealLine.builder()
                    .description(lr.description().trim())
                    .hsnCode(lr.hsnCode())
                    .quantity(lr.quantity())
                    .unitPrice(lr.unitPrice())
                    .taxRate(rate)
                    .untaxedAmount(amounts.untaxed())
                    .taxAmount(amounts.tax())
                    .cgstAmount(split.cgst())
                    .sgstAmount(split.sgst())
                    .igstAmount(split.igst())
                    .lineTotal(amounts.total())
                    .build());
        }

        deal.recalculateTotals();
        return mapper.toDealResponse(dealRepository.save(deal), buyer.getId());
    }

    /** Send the request to the supplier. Only the originator may do this. */
    @Transactional
    public TradeDtos.DealResponse send(Long id) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = requireDeal(id);

        if (!deal.getInitiatedBy().getId().equals(partyId)) {
            throw new ApiExceptions.ForbiddenException("Only the party who raised this request can send it");
        }
        if (deal.getStatus() != DealStatus.RFQ_DRAFT) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only a draft request can be sent (this one is " + deal.getStatus() + ")");
        }
        if (deal.getLines().isEmpty()) {
            throw new ApiExceptions.BusinessRuleException("Add at least one line before sending");
        }

        deal.setStatus(DealStatus.RFQ_SENT);
        return mapper.toDealResponse(dealRepository.save(deal), partyId);
    }

    /**
     * Accept the request. Only the counterparty may accept — the whole
     * point of a quotation is that the other side agrees to it.
     */
    @Transactional
    public TradeDtos.DealResponse accept(Long id) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = requireDeal(id);
        assertCanDecide(deal, partyId);

        deal.setStatus(DealStatus.ACCEPTED);
        Deal saved = dealRepository.save(deal);

        // Acceptance is what turns a proposal into an order, so the order
        // paperwork is drawn up now — in both books at once if both sides
        // keep them.
        documentService.createOrderDocuments(saved);

        return mapper.toDealResponse(saved, partyId);
    }

    @Transactional
    public TradeDtos.DealResponse reject(Long id, TradeDtos.RejectRequest request) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = requireDeal(id);
        assertCanDecide(deal, partyId);

        deal.setStatus(DealStatus.REJECTED);
        deal.setRejectReason(request == null ? null : request.reason());
        return mapper.toDealResponse(dealRepository.save(deal), partyId);
    }

    /**
     * Mark the goods handed over. Only the supplying side can say this —
     * the buyer confirming their own delivery would be self-certifying.
     * <p>
     * Delivery has no ledger effect, but it gates invoicing: you cannot
     * bill for something you have not delivered.
     */
    @Transactional
    public TradeDtos.DealResponse markDelivered(Long id, TradeDtos.DeliverRequest request) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = requireDeal(id);

        if (!deal.getSeller().getId().equals(partyId)) {
            throw new ApiExceptions.ForbiddenException(
                    "Only the supplying party can mark this delivered");
        }
        if (deal.getStatus() != DealStatus.ACCEPTED) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only an accepted order can be delivered (this one is " + deal.getStatus() + ")");
        }

        deal.setStatus(DealStatus.DELIVERED);
        deal.setDeliveredAt(request != null && request.deliveredAt() != null
                ? request.deliveredAt() : LocalDate.now());
        return mapper.toDealResponse(dealRepository.save(deal), partyId);
    }

    @Transactional
    public TradeDtos.DealResponse cancel(Long id) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = requireDeal(id);
        assertInvolved(deal, partyId);

        if (!deal.getStatus().canCancel()) {
            throw new ApiExceptions.BusinessRuleException(
                    "A deal at status " + deal.getStatus() + " cannot be cancelled — "
                            + "its ledger entry already exists, so correct it with a reversal instead");
        }

        deal.setStatus(DealStatus.CANCELLED);
        // Cancel any order paperwork already drawn up. Orders carry no
        // ledger effect, so nothing needs reversing.
        for (TradeDocument doc : documentRepository.findByDealId(deal.getId())) {
            if (doc.getDocType().isOrder()) {
                doc.setStatus(DocumentStatus.CANCELLED);
                documentRepository.save(doc);
            }
        }
        return mapper.toDealResponse(dealRepository.save(deal), partyId);
    }

    // ---------------- internals ----------------

    Deal requireDeal(Long id) {
        return dealRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Deal", id));
    }

    private void assertInvolved(Deal deal, Long partyId) {
        if (!deal.involves(partyId)) {
            throw new ApiExceptions.ForbiddenException("This deal belongs to other parties");
        }
    }

    private void assertCanDecide(Deal deal, Long partyId) {
        assertInvolved(deal, partyId);
        if (deal.getStatus() != DealStatus.RFQ_SENT) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only a sent request can be accepted or rejected (this one is " + deal.getStatus() + ")");
        }
        if (deal.getInitiatedBy().getId().equals(partyId)) {
            throw new ApiExceptions.ForbiddenException(
                    "You raised this request — the other party decides on it");
        }
    }

    /** Deals that reached a state where documents should exist. */
    List<Deal> allForParty(Long partyId) {
        return dealRepository.findAll().stream().filter(d -> d.involves(partyId)).toList();
    }
}
