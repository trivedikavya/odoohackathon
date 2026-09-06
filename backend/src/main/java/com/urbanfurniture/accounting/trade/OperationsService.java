package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.analytic.AnalyticAccount;
import com.urbanfurniture.accounting.analytic.AnalyticAccountRepository;
import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.PlatformSequenceService;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.identity.PartyRepository;
import com.urbanfurniture.accounting.ledger.Account;
import com.urbanfurniture.accounting.ledger.AccountLookup;
import com.urbanfurniture.accounting.ledger.AccountRepository;
import com.urbanfurniture.accounting.ledger.JournalEntry;
import com.urbanfurniture.accounting.ledger.JournalEntryDraft;
import com.urbanfurniture.accounting.ledger.JournalLookup;
import com.urbanfurniture.accounting.ledger.JournalPostingService;
import com.urbanfurniture.accounting.ledger.JournalType;
import com.urbanfurniture.accounting.ledger.SourceType;
import com.urbanfurniture.accounting.ledger.SystemAccount;
import com.urbanfurniture.accounting.master.Product;
import com.urbanfurniture.accounting.master.ProductRepository;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.stock.StockService;
import com.urbanfurniture.accounting.tax.GstSplit;
import com.urbanfurniture.accounting.tax.TaxCalculator;
import com.urbanfurniture.accounting.tax.TaxTreatment;
import com.urbanfurniture.accounting.stock.StockSource;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The entries a business needs that are not a trade with a counterparty.
 * <p>
 * Without these a book can only ever contain sales and purchases: it
 * starts with no money, has no way to record rent or salaries, and no way
 * to enter stock it already owned. The result balances but describes a
 * business that cannot exist.
 */
@Service
@RequiredArgsConstructor
public class OperationsService {

    private final JournalPostingService journalPostingService;
    private final AccountLookup accounts;
    private final AccountRepository accountRepository;
    private final JournalLookup journals;
    private final ProductRepository productRepository;
    private final AnalyticAccountRepository analyticAccountRepository;
    private final StockService stockService;
    private final CurrentUser currentUser;
    private final PartyRepository partyRepository;
    private final com.urbanfurniture.accounting.identity.BookRepository bookRepository;
    private final DealRepository dealRepository;
    private final DocumentService documentService;
    private final PlatformSequenceService platformSequenceService;
    private final TradeMapper mapper;

    /**
     * Money the owner puts into the business.
     * <pre>
     *   Dr  Cash / Bank
     *       Cr  Owner's Capital
     * </pre>
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public JournalEntry contributeCapital(OperationsDtos.CapitalRequest request) {
        Book book = currentUser.requireBook();
        BigDecimal amount = Money.of(request.amount());
        if (!Money.isPositive(amount)) {
            throw new ApiExceptions.BusinessRuleException("Capital must be a positive amount");
        }

        return journalPostingService.post(JournalEntryDraft.on(
                        book,
                        journals.require(book, request.method().journal()),
                        request.date(),
                        SourceType.OPENING,
                        null,
                        request.note() == null ? "Owner capital introduced" : request.note())
                .debit(accounts.require(book, request.method().account()), amount,
                        request.method().name() + " introduced")
                .credit(accounts.require(book, SystemAccount.CAPITAL), amount,
                        "Owner's capital"));
    }

    /**
     * Stock the book already owned when it started keeping accounts.
     * <pre>
     *   Dr  Inventory
     *       Cr  Owner's Capital
     * </pre>
     * The stock movement and the ledger entry carry the same value, which
     * is what keeps the two tied together from the very first row.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public JournalEntry openingStock(OperationsDtos.OpeningStockRequest request) {
        Book book = currentUser.requireBook();
        Product product = requireOwnProduct(book, request.productId());

        if (!product.tracksStock()) {
            throw new ApiExceptions.BusinessRuleException(
                    "'" + product.getName() + "' does not carry stock");
        }
        BigDecimal value = Money.of(request.unitCost().multiply(request.quantity()));

        JournalEntry entry = journalPostingService.post(JournalEntryDraft.on(
                        book,
                        journals.require(book, JournalType.GENERAL),
                        request.date(),
                        SourceType.OPENING,
                        product.getId(),
                        "Opening stock — " + product.getName())
                .debit(accounts.require(book, SystemAccount.INVENTORY), value,
                        "Opening stock — " + product.getName())
                .credit(accounts.require(book, SystemAccount.CAPITAL), value,
                        "Owner's capital"));

        stockService.receive(book, product, request.quantity(), request.unitCost(),
                request.date(), StockSource.OPENING, null, entry, "Opening stock");

        return entry;
    }

    /**
     * Rent, salaries, utilities — an overhead paid straight out of cash
     * or bank, with no vendor document behind it.
     * <pre>
     *   Dr  the chosen expense account
     *       Cr  Cash / Bank
     * </pre>
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public JournalEntry recordExpense(OperationsDtos.ExpenseRequest request) {
        Book book = currentUser.requireBook();
        BigDecimal amount = Money.of(request.amount());
        if (!Money.isPositive(amount)) {
            throw new ApiExceptions.BusinessRuleException("An expense must be a positive amount");
        }

        Account expenseAccount = accountRepository.findById(request.expenseAccountId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Account", request.expenseAccountId()));
        currentUser.assertOwnsBook(expenseAccount.getBook().getId());

        if (expenseAccount.getType() != com.urbanfurniture.accounting.ledger.AccountType.EXPENSE) {
            throw new ApiExceptions.BusinessRuleException(
                    "'" + expenseAccount.getName() + "' is not an expense account");
        }

        AnalyticAccount analytic = null;
        if (request.analyticAccountId() != null) {
            analytic = analyticAccountRepository.findById(request.analyticAccountId())
                    .orElseThrow(() -> new ApiExceptions.NotFoundException(
                            "Analytic account", request.analyticAccountId()));
            currentUser.assertOwnsBook(analytic.getBook().getId());
        }

        return journalPostingService.post(JournalEntryDraft.on(
                        book,
                        journals.require(book, request.method().journal()),
                        request.date(),
                        SourceType.MANUAL,
                        null,
                        request.description())
                .debit(expenseAccount, null, analytic, amount, request.description())
                .credit(accounts.require(book, request.method().account()), amount,
                        "Paid by " + request.method().name().toLowerCase()));
    }

    /**
     * Records a completed purchase from a supplier who is not on the
     * platform. The caller is the buyer.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public TradeDtos.DealResponse directPurchase(OperationsDtos.DirectTradeRequest request) {
        return recordDirect(request, false);
    }

    /**
     * Records a counter sale to a walk-in customer. The caller is the
     * seller.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public TradeDtos.DealResponse directSale(OperationsDtos.DirectTradeRequest request) {
        return recordDirect(request, true);
    }

    private TradeDtos.DealResponse recordDirect(OperationsDtos.DirectTradeRequest request,
                                                boolean iAmSeller) {
        Long myPartyId = currentUser.requirePartyId();
        Party me = partyRepository.findById(myPartyId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Party", myPartyId));
        Party other = partyRepository.findById(request.counterpartyPartyId())
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Party", request.counterpartyPartyId()));

        if (me.getId().equals(other.getId())) {
            throw new ApiExceptions.BusinessRuleException("You cannot trade with yourself");
        }

        // Direct entry is for counterparties who are not here to agree
        // anything. Using it against a registered party would record a
        // claim in our books that never appears in theirs — the two
        // ledgers would diverge, and the reconciliation report would
        // (correctly) start failing. Registered parties go through the
        // request-and-accept flow, which writes both sides together.
        // Tested by whether a book actually exists, not by the party's
        // type: a vendor you added to your own address book is typed
        // VENDOR but has never registered, and trading with them directly
        // is exactly what this path is for.
        if (bookRepository.findByPartyId(other.getId()).isPresent()) {
            throw new ApiExceptions.BusinessRuleException(
                    "'" + other.getName() + "' keeps books on this platform — "
                            + "raise a request instead, so both sides are recorded");
        }

        Party buyer = iAmSeller ? other : me;
        Party seller = iAmSeller ? me : other;

        // The GST treatment is decided exactly as it is on a negotiated
        // deal, so a counter sale is taxed the same as any other.
        String placeOfSupply = buyer.getState();
        TaxTreatment treatment = TaxCalculator.treatmentFor(seller.getState(), placeOfSupply);

        Deal deal = Deal.builder()
                .dealNo(platformSequenceService.next(PlatformSequenceService.DEAL))
                .buyer(buyer)
                .seller(seller)
                .initiatedBy(me)
                .status(DealStatus.ACCEPTED)
                .dealDate(request.date())
                .deliveredAt(request.date())
                .placeOfSupply(placeOfSupply)
                .notes(request.notes())
                .build();

        Book myBook = currentUser.requireBook();

        for (TradeDtos.DealLineRequest lr : request.lines()) {
            // Both directions pick from the caller's OWN catalogue: on a
            // counter sale you sell your own stock, and on a direct
            // purchase you are recording more of an item you carry. The
            // counterparty is offline and may have no catalogue at all.
            Product product = requireOwnProduct(myBook, lr.productId());

            BigDecimal rate = lr.taxRate() != null ? lr.taxRate()
                    : product != null ? product.getTaxRate() : BigDecimal.ZERO;
            BigDecimal price = lr.unitPrice() != null ? lr.unitPrice()
                    : product != null ? product.getSalesPrice() : BigDecimal.ZERO;
            String description = lr.description() != null && !lr.description().isBlank()
                    ? lr.description().trim()
                    : product != null ? product.getName() : null;

            if (description == null) {
                throw new ApiExceptions.BusinessRuleException(
                        "Each line needs either a product or a description");
            }

            LineAmounts amounts = LineAmounts.compute(lr.quantity(), price, rate);
            GstSplit split = TaxCalculator.split(amounts.tax(), treatment);

            deal.addLine(DealLine.builder()
                    .product(product)
                    .description(description)
                    .hsnCode(lr.hsnCode() != null ? lr.hsnCode()
                            : product != null ? product.getHsnCode() : null)
                    .quantity(lr.quantity())
                    .unitPrice(price)
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
        Deal saved = dealRepository.save(deal);

        documentService.recordDirectTrade(saved, myBook, iAmSeller, request.date(),
                request.dueDate() != null ? request.dueDate() : request.date().plusDays(30));

        return mapper.toDealResponse(saved, myPartyId);
    }

    /** Null id means an ad-hoc line, which is allowed and simply carries no stock. */
    private Product requireOwnProduct(Book book, Long productId) {
        if (productId == null) {
            return null;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Product", productId));
        if (!product.getBook().getId().equals(book.getId())) {
            throw new ApiExceptions.ForbiddenException("That product belongs to another book");
        }
        return product;
    }

    /** Kept so the compiler sees the date import in every branch. */
    @SuppressWarnings("unused")
    private static final LocalDate EPOCH_HINT = LocalDate.EPOCH;
}
