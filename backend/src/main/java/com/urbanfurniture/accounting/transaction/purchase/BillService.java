package com.urbanfurniture.accounting.transaction.purchase;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.DocumentNumberService;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import com.urbanfurniture.accounting.ledger.JournalEntry;
import com.urbanfurniture.accounting.ledger.JournalEntryDraft;
import com.urbanfurniture.accounting.ledger.JournalPostingService;
import com.urbanfurniture.accounting.ledger.SourceType;
import com.urbanfurniture.accounting.master.account.AccountLookup;
import com.urbanfurniture.accounting.master.account.SystemAccount;
import com.urbanfurniture.accounting.master.contact.Contact;
import com.urbanfurniture.accounting.master.contact.ContactService;
import com.urbanfurniture.accounting.master.journal.JournalService;
import com.urbanfurniture.accounting.master.journal.JournalType;
import com.urbanfurniture.accounting.master.product.Product;
import com.urbanfurniture.accounting.master.product.ProductService;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.transaction.DocumentStatus;
import com.urbanfurniture.accounting.transaction.LineAmounts;
import com.urbanfurniture.accounting.transaction.OrderStatus;
import com.urbanfurniture.accounting.transaction.TransactionDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ContactService contactService;
    private final ProductService productService;
    private final DocumentNumberService documentNumberService;
    private final JournalPostingService journalPostingService;
    private final JournalService journalService;
    private final AccountLookup accounts;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResponse<TransactionDtos.DocumentResponse> search(String search, DocumentStatus status,
                                                                 Pageable pageable) {
        String term = SearchTerms.normalize(search);
        return PageResponse.of(
                billRepository.search(term, status, currentUser.contactScopeOrNull(), pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.DocumentResponse get(Long id) {
        Bill bill = requireBill(id);
        currentUser.assertCanAccessContact(bill.getContact().getId());
        return toResponse(bill);
    }

    /** Standalone vendor bill, not originating from a purchase order. */
    @Transactional
    public TransactionDtos.DocumentResponse create(TransactionDtos.DocumentRequest request) {
        Contact vendor = contactService.requireContact(request.contactId());
        if (!vendor.canBeVendor()) {
            throw new ApiExceptions.BusinessRuleException("Contact '" + vendor.getName() + "' is not a vendor");
        }

        Bill bill = Bill.builder()
                .billNo(documentNumberService.next(DocumentType.BILL))
                .contact(vendor)
                .billDate(request.documentDate())
                .dueDate(request.dueDate())
                .status(DocumentStatus.DRAFT)
                .notes(request.notes())
                .build();

        for (TransactionDtos.LineRequest lr : request.lines()) {
            Product product = productService.requireProduct(lr.productId());
            BigDecimal rate = lr.taxRate() != null ? lr.taxRate() : product.getTaxRate();
            LineAmounts amounts = LineAmounts.compute(lr.quantity(), lr.unitPrice(), rate);
            bill.addLine(BillLine.builder()
                    .product(product)
                    .quantity(lr.quantity())
                    .unitPrice(Money.of(lr.unitPrice()))
                    .taxRate(rate)
                    .untaxedAmount(amounts.untaxed())
                    .taxAmount(amounts.tax())
                    .lineTotal(amounts.total())
                    .build());
        }

        recalculateTotals(bill);
        return toResponse(billRepository.save(bill));
    }

    /** Converts a confirmed purchase order into a draft vendor bill. */
    @Transactional
    public TransactionDtos.DocumentResponse createFromPurchaseOrder(Long purchaseOrderId,
                                                                    TransactionDtos.ConvertRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findWithLinesById(purchaseOrderId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Purchase order", purchaseOrderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiExceptions.BusinessRuleException("A cancelled purchase order cannot be billed");
        }
        if (order.getStatus() == OrderStatus.BILLED) {
            throw new ApiExceptions.BusinessRuleException(
                    "Purchase order " + order.getOrderNo() + " has already been billed");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApiExceptions.BusinessRuleException(
                    "Confirm purchase order " + order.getOrderNo() + " before billing it");
        }

        LocalDate billDate = request != null && request.documentDate() != null
                ? request.documentDate() : LocalDate.now();

        Bill bill = Bill.builder()
                .billNo(documentNumberService.next(DocumentType.BILL))
                .purchaseOrder(order)
                .contact(order.getContact())
                .billDate(billDate)
                .dueDate(request != null ? request.dueDate() : null)
                .status(DocumentStatus.DRAFT)
                .notes(order.getNotes())
                .build();

        for (PurchaseOrderLine ol : order.getLines()) {
            bill.addLine(BillLine.builder()
                    .product(ol.getProduct())
                    .quantity(ol.getQuantity())
                    .unitPrice(ol.getUnitPrice())
                    .taxRate(ol.getTaxRate())
                    .untaxedAmount(ol.getUntaxedAmount())
                    .taxAmount(ol.getTaxAmount())
                    .lineTotal(ol.getLineTotal())
                    .build());
        }

        recalculateTotals(bill);
        Bill saved = billRepository.save(bill);

        order.setStatus(OrderStatus.BILLED);
        purchaseOrderRepository.save(order);

        return toResponse(saved);
    }

    /**
     * Posts the bill to the ledger.
     * <p>
     * Double-entry mapping for a vendor bill:
     * <pre>
     *   Dr  Purchase Expense                untaxed
     *   Dr  Input Tax Credit                tax
     *       Cr  Creditors (Accounts Payable)        total (incl. tax)
     * </pre>
     */
    @Transactional
    public TransactionDtos.DocumentResponse post(Long id) {
        Bill bill = requireBill(id);

        if (bill.getStatus() != DocumentStatus.DRAFT) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only a draft bill can be posted (this one is " + bill.getStatus() + ")");
        }
        if (!Money.isPositive(bill.getTotalAmount())) {
            throw new ApiExceptions.BusinessRuleException("Cannot post a bill with a zero total");
        }

        JournalEntryDraft draft = JournalEntryDraft.on(
                        journalService.requireByType(JournalType.PURCHASE),
                        bill.getBillDate(),
                        SourceType.BILL,
                        bill.getId(),
                        "Vendor bill " + bill.getBillNo() + " - " + bill.getContact().getName())
                .debit(accounts.require(SystemAccount.PURCHASE_EXPENSE),
                        bill.getUntaxedAmount(), "Purchases - " + bill.getBillNo())
                .debit(accounts.require(SystemAccount.TAX_RECEIVABLE),
                        bill.getTaxAmount(), "Input tax - " + bill.getBillNo())
                .credit(accounts.require(SystemAccount.CREDITORS), bill.getContact(),
                        bill.getTotalAmount(), "Payable to " + bill.getContact().getName());

        JournalEntry entry = journalPostingService.post(draft);

        bill.setJournalEntry(entry);
        bill.setStatus(DocumentStatus.POSTED);
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public TransactionDtos.DocumentResponse cancel(Long id) {
        Bill bill = requireBill(id);
        if (bill.getStatus().isPosted()) {
            throw new ApiExceptions.BusinessRuleException(
                    "A posted bill cannot be cancelled - its ledger entry is already recorded");
        }
        bill.setStatus(DocumentStatus.CANCELLED);
        return toResponse(billRepository.save(bill));
    }

    // ---------------- internals ----------------

    Bill requireBill(Long id) {
        return billRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Bill", id));
    }

    private void recalculateTotals(Bill bill) {
        BigDecimal untaxed = Money.ZERO;
        BigDecimal tax = Money.ZERO;
        BigDecimal total = Money.ZERO;
        for (BillLine line : bill.getLines()) {
            untaxed = Money.add(untaxed, line.getUntaxedAmount());
            tax = Money.add(tax, line.getTaxAmount());
            total = Money.add(total, line.getLineTotal());
        }
        bill.setUntaxedAmount(untaxed);
        bill.setTaxAmount(tax);
        bill.setTotalAmount(total);
    }

    TransactionDtos.DocumentResponse toResponse(Bill bill) {
        List<TransactionDtos.LineResponse> lines = bill.getLines().stream()
                .map(l -> new TransactionDtos.LineResponse(
                        l.getId(), l.getLineNo(), l.getProduct().getId(), l.getProduct().getName(),
                        l.getQuantity(), l.getUnitPrice(), l.getTaxRate(),
                        l.getUntaxedAmount(), l.getTaxAmount(), l.getLineTotal()))
                .toList();

        return new TransactionDtos.DocumentResponse(
                bill.getId(), bill.getBillNo(),
                bill.getContact().getId(), bill.getContact().getName(),
                bill.getBillDate(), bill.getDueDate(), bill.getStatus(),
                bill.getUntaxedAmount(), bill.getTaxAmount(), bill.getTotalAmount(),
                bill.getAmountPaid(), bill.amountDue(),
                bill.getJournalEntry() == null ? null : bill.getJournalEntry().getId(),
                bill.getJournalEntry() == null ? null : bill.getJournalEntry().getEntryNo(),
                bill.getPurchaseOrder() == null ? null : bill.getPurchaseOrder().getId(),
                bill.getPurchaseOrder() == null ? null : bill.getPurchaseOrder().getOrderNo(),
                bill.getNotes(), lines);
    }
}
