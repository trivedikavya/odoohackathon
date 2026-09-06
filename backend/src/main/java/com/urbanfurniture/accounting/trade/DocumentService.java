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
import com.urbanfurniture.accounting.master.ContactRelationship;
import com.urbanfurniture.accounting.master.ContactRepository;
import com.urbanfurniture.accounting.master.Product;
import com.urbanfurniture.accounting.master.ProductRepository;
import com.urbanfurniture.accounting.master.ProductType;
import com.urbanfurniture.accounting.stock.StockMovement;
import com.urbanfurniture.accounting.stock.StockMovementRepository;
import com.urbanfurniture.accounting.stock.StockService;
import com.urbanfurniture.accounting.stock.StockSource;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.tax.GstTotals;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final StockMovementRepository stockMovementRepository;
    private final ReferenceDataProvisioner provisioner;
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

    /**
     * Records a trade that has already happened, in one step.
     * <p>
     * The RFQ round trip exists so two registered parties can agree
     * terms. It is the wrong shape for a purchase from an offline
     * supplier or a counter sale to a walk-in customer, where there is
     * nobody on the other side to accept anything — so this path creates
     * the order, the delivery and the invoice together.
     * <p>
     * The actor checks are the caller's job here; what this method still
     * guarantees is the accounting: the same documents, the same stock
     * movements and the same entries as the long route, so a direct sale
     * and a negotiated one are indistinguishable in the ledger.
     */
    @Transactional
    public void recordDirectTrade(Deal deal, Book myBook, boolean iAmSeller,
                                  LocalDate docDate, LocalDate dueDate) {
        // Strictly single-sided. Direct entry means "I am recording this
        // myself" — the counterparty is not participating, and may not
        // even hold an account here. Writing into their books on their
        // behalf would put stock they never had on their shelf.
        deal.setStatus(DealStatus.DELIVERED);
        deal.setDeliveredAt(docDate);
        dealRepository.save(deal);

        if (iAmSeller) {
            // The sales order is what the stock issue reads its lines from.
            ensureDocument(deal, myBook, DocumentType.SALES_ORDER, deal.getBuyer(), docDate, null);
            issueStockForDelivery(deal);
            postInvoice(ensureDocument(deal, myBook, DocumentType.INVOICE,
                    deal.getBuyer(), docDate, dueDate));
        } else {
            ensureDocument(deal, myBook, DocumentType.PURCHASE_ORDER, deal.getSeller(), docDate, null);
            postBill(ensureDocument(deal, myBook, DocumentType.BILL,
                    deal.getSeller(), docDate, dueDate));
        }

        deal.setStatus(DealStatus.INVOICED);
        dealRepository.save(deal);
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
     *   Dr  Inventory                  stocked goods, at cost
     *   Dr  Purchase Expense           services and untracked items
     *   Dr  CGST + SGST Input Credit   tax   (intra-state)
     *   Dr  IGST Input Credit          tax   (inter-state)
     *   Dr  Input GST                  any tax not attributable
     *       Cr  Accounts Payable                   total (incl. tax)
     * </pre>
     * <p>
     * Buying stock is not an expense — it converts one asset (cash, or a
     * promise to pay) into another (goods on the shelf). The cost only
     * becomes an expense when the goods are sold, which is what
     * {@link #issueStockForDelivery} does. Expensing purchases on receipt
     * is what makes a profit figure lurch about with buying patterns
     * rather than tracking trade.
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

        // Split the untaxed value by where it belongs: stocked goods
        // capitalise, everything else expenses.
        BigDecimal stocked = Money.ZERO;
        for (TradeDocumentLine line : bill.getLines()) {
            if (line.getProduct() != null && line.getProduct().tracksStock()) {
                stocked = Money.add(stocked, line.getUntaxedAmount());
            }
        }
        draft.debit(accounts.require(book, SystemAccount.INVENTORY), stocked,
                "Goods received — " + bill.getDocNo());

        untaxedByProject(bill, false).forEach((analytic, amount) -> draft.debit(
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

        // The goods are now on the shelf, at what was actually paid.
        receiveStockForBill(bill, entry);
    }

    /**
     * Books the goods on a posted bill into stock, at the price paid.
     * <p>
     * The value written here is exactly the value debited to Inventory
     * above, which is what keeps the stock ledger and the general ledger
     * tied together — a tie-out the Stock Ledger report publishes rather
     * than assumes.
     */
    private void receiveStockForBill(TradeDocument bill, JournalEntry entry) {
        for (TradeDocumentLine line : bill.getLines()) {
            Product product = line.getProduct();
            if (product == null || !product.tracksStock()) {
                continue;
            }
            BigDecimal unitCost = line.getQuantity().signum() == 0
                    ? Money.ZERO
                    : line.getUntaxedAmount().divide(line.getQuantity(), Money.SCALE, RoundingMode.HALF_UP);

            stockService.receive(bill.getBook(), product, line.getQuantity(), unitCost,
                    bill.getDocDate(), StockSource.BILL, bill.getId(), entry,
                    "Received on " + bill.getDocNo());
        }
    }

    /**
     * Moves stock out when a sale is delivered, and expenses it.
     * <pre>
     *   Dr  Cost of Goods Sold    at weighted-average cost
     *       Cr  Inventory
     * </pre>
     * <p>
     * This runs at <b>delivery</b>, not invoicing, because delivery is
     * when the goods actually leave. Waiting for the invoice would show
     * stock still on hand after it had been shipped. Revenue is
     * recognised separately when the invoice posts; the deal detail shows
     * both entries together, which is what makes the margin on a sale
     * visible.
     * <p>
     * Returns silently when the selling book holds no stocked items on
     * the deal — a pure services sale has no cost of goods.
     */
    @Transactional
    public void issueStockForDelivery(Deal deal) {
        Book sellerBook = bookOf(deal.getSeller()).orElse(null);
        if (sellerBook == null) {
            return;
        }
        TradeDocument salesOrder = documentRepository
                .findByDealIdAndBookIdAndDocType(deal.getId(), sellerBook.getId(), DocumentType.SALES_ORDER)
                .orElse(null);
        if (salesOrder == null) {
            return;
        }

        // Expand combos to their components and total them, so a document
        // listing the same item twice is checked against its real total.
        List<StockService.Consumption> consumptions = new ArrayList<>();
        for (TradeDocumentLine line : salesOrder.getLines()) {
            consumptions.addAll(stockService.expand(line.getProduct(), line.getQuantity()));
        }
        Map<Product, BigDecimal> required = stockService.aggregate(consumptions);
        if (required.isEmpty()) {
            return;
        }

        // Check the whole delivery before writing any of it, so a
        // five-line order cannot half-ship and then fail.
        stockService.assertAvailable(sellerBook, required);

        // Issue first: the movements decide the cost, and the journal
        // entry has to carry exactly that figure.
        List<StockMovement> issued = new ArrayList<>();
        BigDecimal totalCost = Money.ZERO;
        for (Map.Entry<Product, BigDecimal> e : required.entrySet()) {
            StockMovement movement = stockService.issue(sellerBook, e.getKey(), e.getValue(),
                    deal.getDeliveredAt(), StockSource.DELIVERY, salesOrder.getId(), null,
                    "Delivered on " + deal.getDealNo());
            issued.add(movement);
            totalCost = Money.add(totalCost, movement.getTotalCost());
        }

        if (!Money.isPositive(totalCost)) {
            return;
        }

        JournalEntryDraft draft = JournalEntryDraft.on(
                        sellerBook,
                        journals.require(sellerBook, JournalType.GENERAL),
                        deal.getDeliveredAt(),
                        SourceType.DELIVERY,
                        salesOrder.getId(),
                        "Cost of goods delivered — " + deal.getDealNo())
                .debit(accounts.require(sellerBook, SystemAccount.COGS), totalCost,
                        "Cost of sales — " + deal.getDealNo())
                .credit(accounts.require(sellerBook, SystemAccount.INVENTORY), totalCost,
                        "Stock issued — " + deal.getDealNo());

        JournalEntry entry = journalPostingService.post(draft);
        issued.forEach(m -> m.setJournalEntry(entry));
        stockMovementRepository.saveAll(issued);
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

        Contact contact = resolveContact(book, counterparty, !docType.isSellSide());

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
                    .product(resolveProductForBook(book, dl))
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
     * The catalogue item this line should move against, in <em>this</em>
     * book.
     * <p>
     * The deal names the seller's product, because that is what the two
     * sides agreed on. The seller's own document can use it directly. The
     * buyer cannot: their stock lives under their own catalogue entry, so
     * the item is matched by name and created if it is new. Buying
     * something you do not stock therefore adds it to your catalogue,
     * which is both what a user expects and the only way the buyer's
     * inventory has anywhere to land.
     */
    private Product resolveProductForBook(Book book, DealLine dealLine) {
        Product sellersProduct = dealLine.getProduct();
        if (sellersProduct == null) {
            // An ad-hoc line — nothing to stock.
            return null;
        }
        if (sellersProduct.getBook().getId().equals(book.getId())) {
            return sellersProduct;
        }
        return productRepository
                .findFirstByBookIdAndNameIgnoreCase(book.getId(), sellersProduct.getName())
                .orElseGet(() -> provisioner.createProduct(Product.builder()
                        .book(book)
                        .name(sellersProduct.getName())
                        // A combo bought in is just goods to the buyer: its
                        // recipe is the seller's business, not theirs.
                        .type(sellersProduct.getType() == ProductType.SERVICE
                                ? ProductType.SERVICE : ProductType.GOODS)
                        .trackInventory(sellersProduct.getType() != ProductType.SERVICE)
                        .salesPrice(BigDecimal.ZERO)
                        .cost(dealLine.getUnitPrice())
                        .hsnCode(sellersProduct.getHsnCode())
                        .taxRate(dealLine.getTaxRate())
                        .category(sellersProduct.getCategory())
                        .active(true)
                        .build()));
    }

    /**
     * A book's contact record for a counterparty, created on first trade.
     * <p>
     * Contacts are book-local, so trading with someone for the first time
     * naturally adds them to your address book rather than requiring the
     * user to key them in twice.
     */
    /**
     * @param theySupplyUs true when this book is buying from them, which
     *                     decides the address-book label
     */
    private Contact resolveContact(Book book, Party counterparty, boolean theySupplyUs) {
        ContactRelationship relationship =
                theySupplyUs ? ContactRelationship.VENDOR : ContactRelationship.CUSTOMER;

        return contactRepository.findByBookIdAndPartyId(book.getId(), counterparty.getId())
                .orElseGet(() -> {
                    try {
                        return provisioner.createContact(book, counterparty, relationship);
                    } catch (DataIntegrityViolationException e) {
                        // Another request created it between our look and
                        // our insert. Theirs is as good as ours.
                        return contactRepository
                                .findByBookIdAndPartyId(book.getId(), counterparty.getId())
                                .orElseThrow(() -> e);
                    }
                });
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
        return untaxedByProject(doc, null);
    }

    /**
     * @param stocked {@code null} for every line; {@code true} or
     *                {@code false} to take only lines whose product does
     *                or does not carry stock. That split is what lets a
     *                bill capitalise its goods and expense its services
     *                from the same document.
     */
    private Map<AnalyticAccount, BigDecimal> untaxedByProject(TradeDocument doc, Boolean stocked) {
        Map<Long, AnalyticAccount> byId = new LinkedHashMap<>();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();

        for (TradeDocumentLine line : doc.getLines()) {
            if (stocked != null) {
                boolean tracks = line.getProduct() != null && line.getProduct().tracksStock();
                if (tracks != stocked) {
                    continue;
                }
            }
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
