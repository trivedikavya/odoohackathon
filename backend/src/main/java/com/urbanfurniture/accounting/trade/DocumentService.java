package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.analytic.AnalyticAccount;
import com.urbanfurniture.accounting.analytic.AnalyticAccountRepository;
import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.BookRepository;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.ledger.AccountLookup;
import com.urbanfurniture.accounting.ledger.JournalEntry;
import com.urbanfurniture.accounting.ledger.JournalEntryDraft;
import com.urbanfurniture.accounting.ledger.JournalLookup;
import com.urbanfurniture.accounting.ledger.JournalPostingService;
import com.urbanfurniture.accounting.ledger.JournalType;
import com.urbanfurniture.accounting.ledger.SourceType;
import com.urbanfurniture.accounting.ledger.SystemAccount;
import com.urbanfurniture.accounting.master.Contact;
import com.urbanfurniture.accounting.master.ContactRepository;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.tax.GstTotals;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns an accepted deal into paperwork, and posts that paperwork to the
 * ledger.
 * <p>
 * <b>Mirroring.</b> When both parties keep books, one deal yields two
 * documents. The seller's Invoice and the buyer's Bill are created
 * together and posted together, each into its own book, each producing
 * its own balanced entry. Neither entry ever references the other book's
 * accounts — {@link JournalPostingService} refuses that outright.
 * <p>
 * When the counterparty is a customer, only the supplying book gets a
 * document, because there is no second set of books to write into.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final TradeDocumentRepository documentRepository;
    private final DealRepository dealRepository;
    private final BookRepository bookRepository;
    private final ContactRepository contactRepository;
    private final AnalyticAccountRepository analyticAccountRepository;
    private final com.urbanfurniture.accounting.common.sequence.BookSequenceService bookSequenceService;
    private final JournalPostingService journalPostingService;
    private final AccountLookup accounts;
    private final JournalLookup journals;
    private final CurrentUser currentUser;
    private final TradeMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<TradeDtos.DocumentResponse> search(DocumentType docType, DocumentStatus status,
                                                           String search, Pageable pageable) {
        Long bookId = currentUser.requireBookId();
        return PageResponse.of(
                documentRepository.search(bookId, docType, status, SearchTerms.normalize(search), pageable),
                mapper::toDocumentResponse);
    }

    @Transactional(readOnly = true)
    public TradeDtos.DocumentResponse get(Long id) {
        TradeDocument doc = requireDocument(id);
        currentUser.assertOwnsBook(doc.getBook().getId());
        return mapper.toDocumentResponse(doc);
    }

    /**
     * Draws up the order paperwork once a request is accepted: a purchase
     * order in the buyer's book and a sales order in the seller's,
     * whichever of those books exist.
     * <p>
     * Idempotent — calling it twice returns the existing documents rather
     * than issuing duplicate numbers.
     */
    @Transactional
    public void createOrderDocuments(Deal deal) {
        bookOf(deal.getBuyer()).ifPresent(book ->
                ensureDocument(deal, book, DocumentType.PURCHASE_ORDER, deal.getSeller(),
                        deal.getDealDate(), null));
        bookOf(deal.getSeller()).ifPresent(book ->
                ensureDocument(deal, book, DocumentType.SALES_ORDER, deal.getBuyer(),
                        deal.getDealDate(), null));
    }

    /**
     * Issues the invoice and posts it.
     * <p>
     * The supplier issues the invoice — that is who raises a demand for
     * payment in a real trade. The buyer's bill is created as its mirror
     * in the same transaction, so the two can never exist independently.
     */
    @Transactional
    public TradeDtos.DealResponse invoice(Long dealId, TradeDtos.InvoiceRequest request) {
        Long partyId = currentUser.requirePartyId();
        Deal deal = dealRepository.findWithLinesById(dealId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Deal", dealId));

        if (!deal.getSeller().getId().equals(partyId)) {
            throw new ApiExceptions.ForbiddenException(
                    "Only the supplying party can invoice this deal");
        }
        if (deal.getStatus() != DealStatus.DELIVERED) {
            throw new ApiExceptions.BusinessRuleException(
                    "Deliver the goods before invoicing (this deal is " + deal.getStatus() + ")");
        }
        if (!Money.isPositive(deal.getTotalAmount())) {
            throw new ApiExceptions.BusinessRuleException("Cannot invoice a deal with a zero total");
        }

        LocalDate docDate = request != null && request.docDate() != null
                ? request.docDate() : LocalDate.now();

        Book sellerBook = bookOf(deal.getSeller()).orElseThrow(() ->
                new ApiExceptions.BusinessRuleException("The supplying party has no books"));
        LocalDate dueDate = resolveDueDate(request, sellerBook, deal.getBuyer(), docDate);

        // Seller's side: the invoice.
        TradeDocument invoice = ensureDocument(deal, sellerBook, DocumentType.INVOICE,
                deal.getBuyer(), docDate, dueDate);
        postInvoice(invoice);

        // Buyer's side: the mirror, if they keep books.
        bookOf(deal.getBuyer()).ifPresent(buyerBook -> {
            TradeDocument bill = ensureDocument(deal, buyerBook, DocumentType.BILL,
                    deal.getSeller(), docDate, dueDate);
            postBill(bill);
        });

        deal.setStatus(DealStatus.INVOICED);
        dealRepository.save(deal);

        return mapper.toDealResponse(deal, partyId);
    }

    /** Attaches a project tag to a line in the caller's own book. */
    @Transactional
    public TradeDtos.DocumentResponse tagLine(Long documentId, TradeDtos.TagLineRequest request) {
        TradeDocument doc = requireDocument(documentId);
        currentUser.assertOwnsBook(doc.getBook().getId());

        TradeDocumentLine line = doc.getLines().stream()
                .filter(l -> l.getId().equals(request.documentLineId()))
                .findFirst()
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Line", request.documentLineId()));

        AnalyticAccount analytic = null;
        if (request.analyticAccountId() != null) {
            analytic = analyticAccountRepository.findById(request.analyticAccountId())
                    .orElseThrow(() -> new ApiExceptions.NotFoundException(
                            "Analytic account", request.analyticAccountId()));
            // A tag from another book would attribute this cost to a
            // project its owner cannot see.
            if (!analytic.getBook().getId().equals(doc.getBook().getId())) {
                throw new ApiExceptions.ForbiddenException(
                        "That analytic account belongs to another set of books");
            }
        }
        line.setAnalyticAccount(analytic);
        return mapper.toDocumentResponse(documentRepository.save(doc));
    }

    // ---------------- posting ----------------

    /**
     * <pre>
     *   Dr  Accounts Receivable        total (incl. tax)
     *       Cr  Sales Income                       untaxed, one line per project
     *       Cr  CGST + SGST Payable                tax   (intra-state)
     *       Cr  IGST Payable                       tax   (inter-state)
     *       Cr  Output GST                         any tax not attributable
     * </pre>
     */
    private void postInvoice(TradeDocument invoice) {
        if (invoice.getStatus().isPosted()) {
            return;
        }
        Book book = invoice.getBook();
        Contact contact = invoice.getContact();

        JournalEntryDraft draft = JournalEntryDraft.on(
                        book,
                        journals.require(book, JournalType.SALES),
                        invoice.getDocDate(),
                        SourceType.INVOICE,
                        invoice.getId(),
                        "Invoice " + invoice.getDocNo() + " to " + counterpartyName(invoice))
                .debit(accounts.require(book, SystemAccount.DEBTORS), contact,
                        invoice.getTotalAmount(), "Receivable from " + counterpartyName(invoice));

        untaxedByProject(invoice).forEach((analytic, amount) -> draft.credit(
                accounts.require(book, SystemAccount.SALES_INCOME), null, analytic,
                amount, "Sales — " + invoice.getDocNo()
                        + (analytic == null ? "" : " [" + analytic.getCode() + "]")));

        GstTotals tax = gstTotals(invoice);
        draft.credit(accounts.require(book, SystemAccount.CGST_PAYABLE), tax.cgst(),
                        "Output CGST — " + invoice.getDocNo())
                .credit(accounts.require(book, SystemAccount.SGST_PAYABLE), tax.sgst(),
                        "Output SGST — " + invoice.getDocNo())
                .credit(accounts.require(book, SystemAccount.IGST_PAYABLE), tax.igst(),
                        "Output IGST — " + invoice.getDocNo())
                .credit(accounts.require(book, SystemAccount.TAX_PAYABLE), tax.unsplit(),
                        "Output tax — " + invoice.getDocNo());

        JournalEntry entry = journalPostingService.post(draft);
        invoice.setJournalEntry(entry);
        invoice.setStatus(DocumentStatus.POSTED);
        documentRepository.save(invoice);
    }

    /**
     * <pre>
     *   Dr  Purchase Expense           untaxed, one line per project
     *   Dr  CGST + SGST Input Credit   tax   (intra-state)
     *   Dr  IGST Input Credit          tax   (inter-state)
     *   Dr  Input GST                  any tax not attributable
     *       Cr  Accounts Payable                   total (incl. tax)
     * </pre>
     */
    private void postBill(TradeDocument bill) {
        if (bill.getStatus().isPosted()) {
            return;
        }
        Book book = bill.getBook();
        Contact contact = bill.getContact();

        JournalEntryDraft draft = JournalEntryDraft.on(
                book,
                journals.require(book, JournalType.PURCHASE),
                bill.getDocDate(),
                SourceType.BILL,
                bill.getId(),
                "Bill " + bill.getDocNo() + " from " + counterpartyName(bill));

        untaxedByProject(bill).forEach((analytic, amount) -> draft.debit(
                accounts.require(book, SystemAccount.PURCHASE_EXPENSE), null, analytic,
                amount, "Purchases — " + bill.getDocNo()
                        + (analytic == null ? "" : " [" + analytic.getCode() + "]")));

        GstTotals tax = gstTotals(bill);
        draft.debit(accounts.require(book, SystemAccount.CGST_RECEIVABLE), tax.cgst(),
                        "Input CGST — " + bill.getDocNo())
                .debit(accounts.require(book, SystemAccount.SGST_RECEIVABLE), tax.sgst(),
                        "Input SGST — " + bill.getDocNo())
                .debit(accounts.require(book, SystemAccount.IGST_RECEIVABLE), tax.igst(),
                        "Input IGST — " + bill.getDocNo())
                .debit(accounts.require(book, SystemAccount.TAX_RECEIVABLE), tax.unsplit(),
                        "Input tax — " + bill.getDocNo())
                .credit(accounts.require(book, SystemAccount.CREDITORS), contact,
                        bill.getTotalAmount(), "Payable to " + counterpartyName(bill));

        JournalEntry entry = journalPostingService.post(draft);
        bill.setJournalEntry(entry);
        bill.setStatus(DocumentStatus.POSTED);
        documentRepository.save(bill);
    }

    // ---------------- internals ----------------

    TradeDocument requireDocument(Long id) {
        return documentRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Document", id));
    }

    private Optional<Book> bookOf(Party party) {
        return party.keepsBooks() ? bookRepository.findByPartyId(party.getId()) : Optional.empty();
    }

    /**
     * Finds or creates one book's document for a deal, copying the agreed
     * lines. The unique constraint on (deal, book, type) is the backstop
     * if two requests race.
     */
    private TradeDocument ensureDocument(Deal deal, Book book, DocumentType docType,
                                         Party counterparty, LocalDate docDate, LocalDate dueDate) {
        Optional<TradeDocument> existing =
                documentRepository.findByDealIdAndBookIdAndDocType(deal.getId(), book.getId(), docType);
        if (existing.isPresent()) {
            return existing.get();
        }

        Contact contact = resolveContact(book, counterparty);

        TradeDocument doc = TradeDocument.builder()
                .deal(deal)
                .book(book)
                .docType(docType)
                .docNo(bookSequenceService.next(book, sequenceTypeFor(docType)))
                .docDate(docDate)
                .dueDate(dueDate)
                .status(DocumentStatus.OPEN)
                .contact(contact)
                .placeOfSupply(deal.getPlaceOfSupply())
                .untaxedAmount(deal.getUntaxedAmount())
                .taxAmount(deal.getTaxAmount())
                .totalAmount(deal.getTotalAmount())
                .build();

        BigDecimal cgst = Money.ZERO;
        BigDecimal sgst = Money.ZERO;
        BigDecimal igst = Money.ZERO;

        for (DealLine dl : deal.getLines()) {
            doc.addLine(TradeDocumentLine.builder()
                    .dealLine(dl)
                    .description(dl.getDescription())
                    .hsnCode(dl.getHsnCode())
                    .quantity(dl.getQuantity())
                    .unitPrice(dl.getUnitPrice())
                    .taxRate(dl.getTaxRate())
                    .untaxedAmount(dl.getUntaxedAmount())
                    .taxAmount(dl.getTaxAmount())
                    .cgstAmount(dl.getCgstAmount())
                    .sgstAmount(dl.getSgstAmount())
                    .igstAmount(dl.getIgstAmount())
                    .lineTotal(dl.getLineTotal())
                    .build());
            cgst = Money.add(cgst, dl.getCgstAmount());
            sgst = Money.add(sgst, dl.getSgstAmount());
            igst = Money.add(igst, dl.getIgstAmount());
        }

        doc.setCgstAmount(cgst);
        doc.setSgstAmount(sgst);
        doc.setIgstAmount(igst);

        return documentRepository.save(doc);
    }

    /**
     * A book's contact record for a counterparty, created on first trade.
     * <p>
     * Contacts are book-local, so trading with someone for the first time
     * naturally adds them to your address book rather than requiring the
     * user to key them in twice.
     */
    private Contact resolveContact(Book book, Party counterparty) {
        return contactRepository.findByBookIdAndPartyId(book.getId(), counterparty.getId())
                .orElseGet(() -> contactRepository.save(Contact.builder()
                        .book(book)
                        .party(counterparty)
                        .creditDays(30)
                        .active(true)
                        .build()));
    }

    private LocalDate resolveDueDate(TradeDtos.InvoiceRequest request, Book sellerBook,
                                     Party buyer, LocalDate docDate) {
        if (request != null && request.dueDate() != null) {
            return request.dueDate();
        }
        return contactRepository.findByBookIdAndPartyId(sellerBook.getId(), buyer.getId())
                .map(c -> docDate.plusDays(c.getCreditDays()))
                .orElse(docDate.plusDays(30));
    }

    private com.urbanfurniture.accounting.common.sequence.DocumentType sequenceTypeFor(DocumentType docType) {
        return switch (docType) {
            case SALES_ORDER -> com.urbanfurniture.accounting.common.sequence.DocumentType.SALES_ORDER;
            case PURCHASE_ORDER -> com.urbanfurniture.accounting.common.sequence.DocumentType.PURCHASE_ORDER;
            case INVOICE -> com.urbanfurniture.accounting.common.sequence.DocumentType.INVOICE;
            case BILL -> com.urbanfurniture.accounting.common.sequence.DocumentType.BILL;
        };
    }

    private String counterpartyName(TradeDocument doc) {
        return doc.getContact() == null ? "counterparty" : doc.getContact().getParty().getName();
    }

    private GstTotals gstTotals(TradeDocument doc) {
        return GstTotals.from(doc.getTaxAmount(), doc.getLines(),
                TradeDocumentLine::getCgstAmount,
                TradeDocumentLine::getSgstAmount,
                TradeDocumentLine::getIgstAmount);
    }

    /**
     * Untaxed amount per project, so one document spanning two projects
     * produces one income (or expense) line each, carrying its own tag.
     * <p>
     * Keyed on the analytic id rather than the entity: JPA hands back lazy
     * proxies, and two proxies for the same row are not guaranteed equal,
     * which would split one project's costs across two ledger lines.
     */
    private Map<AnalyticAccount, BigDecimal> untaxedByProject(TradeDocument doc) {
        Map<Long, AnalyticAccount> byId = new LinkedHashMap<>();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();

        for (TradeDocumentLine line : doc.getLines()) {
            AnalyticAccount analytic = line.getAnalyticAccount();
            Long key = analytic == null ? null : analytic.getId();
            byId.putIfAbsent(key, analytic);
            totals.merge(key, Money.nullSafe(line.getUntaxedAmount()), Money::add);
        }

        Map<AnalyticAccount, BigDecimal> result = new LinkedHashMap<>();
        totals.forEach((key, amount) -> result.put(byId.get(key), amount));
        return result;
    }
}
