package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.BookRepository;
import com.urbanfurniture.accounting.tax.GstTotals;
import com.urbanfurniture.accounting.tax.TaxCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Entity to DTO conversion for the trade module. */
@Component
@RequiredArgsConstructor
public class TradeMapper {

    private final BookRepository bookRepository;
    private final TradeDocumentRepository documentRepository;

    public TradeDtos.DealResponse toDealResponse(Deal deal, Long viewerPartyId) {
        List<TradeDtos.DealLineResponse> lines = deal.getLines().stream()
                .map(l -> new TradeDtos.DealLineResponse(
                        l.getId(), l.getLineNo(), l.getDescription(), l.getHsnCode(),
                        l.getQuantity(), l.getUnitPrice(), l.getTaxRate(),
                        l.getUntaxedAmount(), l.getTaxAmount(),
                        l.getCgstAmount(), l.getSgstAmount(), l.getIgstAmount(),
                        l.getLineTotal()))
                .toList();

        boolean iAmSeller = deal.getSeller().getId().equals(viewerPartyId);
        boolean mirrored = deal.getBuyer().keepsBooks() && deal.getSeller().keepsBooks();

        // Only ever the viewer's own paperwork; the counterparty's
        // document numbers are none of their business.
        List<TradeDtos.DocumentSummary> myDocuments = bookRepository
                .findByPartyId(viewerPartyId)
                .map(Book::getId)
                .map(bookId -> documentRepository.findByDealId(deal.getId()).stream()
                        .filter(d -> d.getBook().getId().equals(bookId))
                        .map(d -> new TradeDtos.DocumentSummary(
                                d.getId(), d.getDocType(), d.getDocNo(), d.getStatus(),
                                d.getTotalAmount(), d.amountDue()))
                        .toList())
                .orElse(List.of());

        return new TradeDtos.DealResponse(
                deal.getId(), deal.getDealNo(),
                deal.getBuyer().getId(), deal.getBuyer().getName(), deal.getBuyer().getType(),
                deal.getSeller().getId(), deal.getSeller().getName(), deal.getSeller().getType(),
                deal.getInitiatedBy().getId(),
                deal.getStatus(), deal.getDealDate(), deal.getExpectedDelivery(), deal.getDeliveredAt(),
                deal.getPlaceOfSupply(),
                TaxCalculator.treatmentFor(deal.getSeller().getState(), deal.getPlaceOfSupply()),
                deal.getUntaxedAmount(), deal.getTaxAmount(), deal.getTotalAmount(),
                deal.getNotes(), deal.getRejectReason(), lines,
                deal.canBeDecidedBy(viewerPartyId),
                iAmSeller,
                mirrored,
                myDocuments);
    }

    public TradeDtos.DocumentResponse toDocumentResponse(TradeDocument doc) {
        List<TradeDtos.DocumentLineResponse> lines = doc.getLines().stream()
                .map(l -> new TradeDtos.DocumentLineResponse(
                        l.getId(), l.getLineNo(), l.getDescription(), l.getHsnCode(),
                        l.getQuantity(), l.getUnitPrice(), l.getTaxRate(),
                        l.getUntaxedAmount(), l.getTaxAmount(),
                        l.getCgstAmount(), l.getSgstAmount(), l.getIgstAmount(), l.getLineTotal(),
                        l.getAnalyticAccount() == null ? null : l.getAnalyticAccount().getId(),
                        l.getAnalyticAccount() == null ? null : l.getAnalyticAccount().getName()))
                .toList();

        // Derived from the lines rather than stored on the header, so the
        // two can never disagree.
        GstTotals tax = GstTotals.from(doc.getTaxAmount(), doc.getLines(),
                TradeDocumentLine::getCgstAmount,
                TradeDocumentLine::getSgstAmount,
                TradeDocumentLine::getIgstAmount);

        return new TradeDtos.DocumentResponse(
                doc.getId(),
                doc.getDeal().getId(), doc.getDeal().getDealNo(),
                doc.getDocType(), doc.getDocNo(), doc.getDocDate(), doc.getDueDate(), doc.getStatus(),
                doc.getContact() == null ? null : doc.getContact().getParty().getId(),
                doc.getContact() == null ? null : doc.getContact().getParty().getName(),
                doc.getPlaceOfSupply(), tax.treatment(),
                doc.getUntaxedAmount(), doc.getTaxAmount(),
                tax.cgst(), tax.sgst(), tax.igst(),
                doc.getTotalAmount(), doc.getAmountSettled(), doc.amountDue(),
                doc.getJournalEntry() == null ? null : doc.getJournalEntry().getId(),
                doc.getJournalEntry() == null ? null : doc.getJournalEntry().getEntryNo(),
                lines);
    }

    public TradeDtos.PaymentResponse toPaymentResponse(Payment p) {
        TradeDocument doc = p.getDocument();
        return new TradeDtos.PaymentResponse(
                p.getId(), p.getPaymentNo(), doc.getId(), doc.getDocNo(), doc.getDocType(),
                doc.getContact() == null ? null : doc.getContact().getParty().getId(),
                doc.getContact() == null ? null : doc.getContact().getParty().getName(),
                p.getDirection(), p.getMethod(), p.getPaymentDate(), p.getAmount(), p.getReference(),
                p.getJournalEntry() == null ? null : p.getJournalEntry().getId(),
                p.getJournalEntry() == null ? null : p.getJournalEntry().getEntryNo());
    }
}
