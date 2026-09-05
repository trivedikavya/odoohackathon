package com.urbanfurniture.accounting.portal;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.AccessLevel;
import com.urbanfurniture.accounting.master.MasterDataService;
import com.urbanfurniture.accounting.master.MasterDtos;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.trade.DealService;
import com.urbanfurniture.accounting.trade.DocumentType;
import com.urbanfurniture.accounting.trade.PaymentRepository;
import com.urbanfurniture.accounting.trade.SettlementService;
import com.urbanfurniture.accounting.trade.TradeDocument;
import com.urbanfurniture.accounting.trade.TradeDocumentRepository;
import com.urbanfurniture.accounting.trade.TradeDtos;
import com.urbanfurniture.accounting.trade.TradeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Self-service for {@code USER} accounts — the customers.
 * <p>
 * A customer keeps no books, so this is scoped by <em>counterparty</em>
 * rather than by book: they see every invoice addressed to them, from
 * every seller and vendor they have bought from, in one place.
 * <p>
 * Every method resolves the scope with {@code requirePartyId()} from the
 * security context and additionally asserts ownership of each row, so a
 * guessed document id returns 403 rather than another customer's invoice.
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Tag(name = "Customer self-service portal")
public class PortalController {

    private final TradeDocumentRepository documentRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementService settlementService;
    private final DealService dealService;
    private final MasterDataService masterDataService;
    private final TradeMapper mapper;
    private final CurrentUser currentUser;

    public record PortalSummary(
            Long partyId, String partyName,
            BigDecimal totalBilled, BigDecimal totalPaid, BigDecimal outstanding,
            long openCount, long overdueCount) {
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public PortalSummary summary() {
        var me = currentUser.require();
        assertPortalUser();

        BigDecimal billed = Money.ZERO;
        BigDecimal paid = Money.ZERO;
        BigDecimal outstanding = Money.ZERO;
        long open = 0;
        long overdue = 0;
        LocalDate today = LocalDate.now();

        for (TradeDocument doc : documentRepository
                .findForCounterparty(me.getPartyId(), Pageable.unpaged()).getContent()) {
            billed = Money.add(billed, doc.getTotalAmount());
            paid = Money.add(paid, doc.getAmountSettled());
            BigDecimal due = doc.amountDue();
            if (Money.isPositive(due)) {
                outstanding = Money.add(outstanding, due);
                open++;
                if (doc.getDueDate() != null && doc.getDueDate().isBefore(today)) {
                    overdue++;
                }
            }
        }

        return new PortalSummary(me.getPartyId(), me.getPartyName(),
                billed, paid, outstanding, open, overdue);
    }

    @GetMapping("/my-documents")
    @Operation(summary = "Every invoice addressed to you, across all suppliers")
    @Transactional(readOnly = true)
    public PageResponse<TradeDtos.DocumentResponse> myDocuments(
            @PageableDefault(size = 50) Pageable pageable) {
        assertPortalUser();
        return PageResponse.of(
                documentRepository.findForCounterparty(currentUser.requirePartyId(), pageable),
                mapper::toDocumentResponse);
    }

    @GetMapping("/my-documents/{id}")
    @Transactional(readOnly = true)
    public TradeDtos.DocumentResponse myDocument(@PathVariable Long id) {
        assertPortalUser();
        TradeDocument doc = requireMine(id);
        return mapper.toDocumentResponse(doc);
    }

    @GetMapping("/my-payments")
    @Transactional(readOnly = true)
    public PageResponse<TradeDtos.PaymentResponse> myPayments(
            @PageableDefault(size = 50) Pageable pageable) {
        assertPortalUser();
        return PageResponse.of(
                paymentRepository.findForCounterparty(currentUser.requirePartyId(), pageable),
                mapper::toPaymentResponse);
    }

    @PostMapping("/my-documents/{id}/pay")
    @Operation(summary = "Settle one of your own invoices")
    public TradeDtos.DealResponse pay(@PathVariable Long id,
                                      @Valid @RequestBody TradeDtos.SettlementRequest request) {
        assertPortalUser();
        TradeDocument doc = requireMine(id);
        // The settlement service re-checks the outstanding amount, so an
        // overpayment is rejected even if this endpoint is called directly.
        return settlementService.settle(doc.getDeal().getId(), request);
    }

    // ---------------- buying ----------------
    //
    // A customer is a buyer, not a spectator: they must be able to
    // approach a seller or a vendor directly. These mirror the staff
    // endpoints but live under /api/portal so a customer never needs
    // access to the back-office namespace.

    @GetMapping("/suppliers")
    @Operation(summary = "Sellers and vendors you can buy from")
    public List<MasterDtos.PartyOption> suppliers() {
        assertPortalUser();
        return masterDataService.supplierOptions();
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Requests and orders you have raised")
    public PageResponse<TradeDtos.DealResponse> myOrders(
            @PageableDefault(size = 25) Pageable pageable) {
        assertPortalUser();
        return dealService.search(null, null, pageable);
    }

    @GetMapping("/my-orders/{id}")
    public TradeDtos.DealResponse myOrder(@PathVariable Long id) {
        assertPortalUser();
        // DealService asserts the caller is a party to the deal.
        return dealService.get(id);
    }

    @PostMapping("/my-orders")
    @Operation(summary = "Raise a request for quotation on a seller or vendor")
    public TradeDtos.DealResponse createOrder(@Valid @RequestBody TradeDtos.CreateDealRequest request) {
        assertPortalUser();
        return dealService.create(request);
    }

    @PostMapping("/my-orders/{id}/send")
    public TradeDtos.DealResponse sendOrder(@PathVariable Long id) {
        assertPortalUser();
        return dealService.send(id);
    }

    @PostMapping("/my-orders/{id}/cancel")
    public TradeDtos.DealResponse cancelOrder(@PathVariable Long id) {
        assertPortalUser();
        return dealService.cancel(id);
    }

    /**
     * Fail closed. Staff reaching this namespace would otherwise be scoped
     * by their own party id and see their own trades through a UI that
     * makes no sense for them.
     */
    private void assertPortalUser() {
        if (currentUser.require().getAccessLevel() != AccessLevel.USER) {
            throw new ApiExceptions.ForbiddenException("The portal is for customer accounts");
        }
    }

    private TradeDocument requireMine(Long id) {
        TradeDocument doc = documentRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Document", id));
        if (doc.getContact() == null) {
            throw new ApiExceptions.ForbiddenException("This document is not addressed to you");
        }
        currentUser.assertIsParty(doc.getContact().getParty().getId());
        return doc;
    }

    /** Kept for the compiler's benefit; see {@link DocumentType}. */
    @SuppressWarnings("unused")
    private static final Class<DocumentType> DOC_TYPE = DocumentType.class;
}
