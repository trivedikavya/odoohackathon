package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.BookSequenceService;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import com.urbanfurniture.accounting.common.sequence.PlatformSequenceService;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.BookRepository;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.identity.PartyRepository;
import com.urbanfurniture.accounting.ledger.AccountLookup;
import com.urbanfurniture.accounting.ledger.JournalEntry;
import com.urbanfurniture.accounting.ledger.JournalEntryDraft;
import com.urbanfurniture.accounting.ledger.JournalLookup;
import com.urbanfurniture.accounting.ledger.JournalPostingService;
import com.urbanfurniture.accounting.ledger.SourceType;
import com.urbanfurniture.accounting.ledger.SystemAccount;
import com.urbanfurniture.accounting.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Records money moving against a deal, and mirrors it.
 * <p>
 * One settlement is simultaneously the seller's receipt and the buyer's
 * disbursement. Recording it once and drawing it into each book means the
 * two sides can never disagree about how much has been paid — which is
 * exactly the disagreement the counterparty reconciliation report exists
 * to detect.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final TradeDocumentRepository documentRepository;
    private final DealRepository dealRepository;
    private final BookRepository bookRepository;
    private final PartyRepository partyRepository;
    private final BookSequenceService bookSequenceService;
    private final PlatformSequenceService platformSequenceService;
    private final JournalPostingService journalPostingService;
    private final AccountLookup accounts;
    private final JournalLookup journals;
    private final CurrentUser currentUser;
    private final TradeMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<TradeDtos.PaymentResponse> search(String search, Pageable pageable) {
        Long bookId = currentUser.requireBookId();
        return PageResponse.of(paymentRepository.search(bookId, SearchTerms.normalize(search), pageable),
                mapper::toPaymentResponse);
    }

    /**
     * Settles part or all of a deal.
     * <p>
     * Either side may record it — the payer knows when they sent the
     * money, the payee knows when it arrived — but whoever records it, the
     * effect on both books is the same.
     */
    @Transactional
    public TradeDtos.DealResponse settle(Long dealId, TradeDtos.SettlementRequest request) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = dealRepository.findWithLinesById(dealId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Deal", dealId));

        if (!deal.involves(partyId)) {
            throw new ApiExceptions.ForbiddenException("This deal belongs to other parties");
        }
        if (!deal.getStatus().hasLedgerEffect()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Invoice this deal before settling it (it is " + deal.getStatus() + ")");
        }

        BigDecimal amount = Money.of(request.amount());

        // The invoice is the authority on what is outstanding: it is the
        // demand for payment, and the bill mirrors it exactly.
        TradeDocument invoice = documentRepository
                .findByDealIdAndBookIdAndDocType(deal.getId(), bookIdOf(deal.getSeller()),
                        com.urbanfurniture.accounting.trade.DocumentType.INVOICE)
                .orElseThrow(() -> new ApiExceptions.BusinessRuleException(
                        "This deal has not been invoiced"));

        BigDecimal outstanding = invoice.amountDue();
        if (Money.gt(amount, outstanding)) {
            throw new ApiExceptions.BusinessRuleException(
                    "Payment of " + amount + " exceeds the outstanding " + outstanding
                            + " on " + invoice.getDocNo());
        }

        Party recordedBy = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Party", partyId));

        Settlement settlement = settlementRepository.save(Settlement.builder()
                .settlementNo(platformSequenceService.next(PlatformSequenceService.SETTLEMENT))
                .deal(deal)
                .settlementDate(request.settlementDate())
                .amount(amount)
                .method(request.method())
                .reference(request.reference())
                .recordedBy(recordedBy)
                .build());

        // Seller's side: money in, receivable cleared.
        recordPayment(settlement, invoice, PaymentDirection.IN, amount);

        // Buyer's side: money out, payable cleared — if they keep books.
        bookOf(deal.getBuyer()).ifPresent(buyerBook ->
                documentRepository.findByDealIdAndBookIdAndDocType(deal.getId(), buyerBook.getId(),
                                com.urbanfurniture.accounting.trade.DocumentType.BILL)
                        .ifPresent(bill -> recordPayment(settlement, bill, PaymentDirection.OUT, amount)));

        // The deal's own status follows the invoice, which is the document
        // that defines what is owed.
        deal.setStatus(Money.isZero(invoice.amountDue()) ? DealStatus.PAID : DealStatus.PARTIALLY_PAID);
        dealRepository.save(deal);

        return mapper.toDealResponse(deal, partyId);
    }

    /**
     * Writes one book's half of a settlement: the payment row, its ledger
     * entry, and the effect on the document's outstanding balance.
     */
    private void recordPayment(Settlement settlement, TradeDocument document,
                               PaymentDirection direction, BigDecimal amount) {
        Book book = document.getBook();
        PaymentMethod method = settlement.getMethod();

        JournalEntryDraft draft = JournalEntryDraft.on(
                book,
                journals.require(book, method.journal()),
                settlement.getSettlementDate(),
                SourceType.PAYMENT,
                document.getId(),
                (direction == PaymentDirection.IN ? "Receipt against " : "Payment against ")
                        + document.getDocNo());

        if (direction == PaymentDirection.IN) {
            draft.debit(accounts.require(book, method.account()), amount,
                            method.name() + " received")
                    .credit(accounts.require(book, SystemAccount.DEBTORS), document.getContact(),
                            amount, "Receivable cleared — " + document.getDocNo());
        } else {
            draft.debit(accounts.require(book, SystemAccount.CREDITORS), document.getContact(),
                            amount, "Payable cleared — " + document.getDocNo())
                    .credit(accounts.require(book, method.account()), amount,
                            method.name() + " paid");
        }

        JournalEntry entry = journalPostingService.post(draft);

        paymentRepository.save(Payment.builder()
                .book(book)
                .settlement(settlement)
                .document(document)
                .paymentNo(bookSequenceService.next(book, DocumentType.PAYMENT))
                .direction(direction)
                .method(method)
                .paymentDate(settlement.getSettlementDate())
                .amount(amount)
                .reference(settlement.getReference())
                .journalEntry(entry)
                .build());

        BigDecimal settled = Money.add(document.getAmountSettled(), amount);
        document.setAmountSettled(settled);
        document.setStatus(Money.eq(settled, document.getTotalAmount())
                ? DocumentStatus.PAID : DocumentStatus.PARTIALLY_PAID);
        documentRepository.save(document);
    }

    private Optional<Book> bookOf(Party party) {
        return party.keepsBooks() ? bookRepository.findByPartyId(party.getId()) : Optional.empty();
    }

    private Long bookIdOf(Party party) {
        return bookOf(party)
                .map(Book::getId)
                .orElseThrow(() -> new ApiExceptions.BusinessRuleException(
                        "'" + party.getName() + "' has no books"));
    }
}
