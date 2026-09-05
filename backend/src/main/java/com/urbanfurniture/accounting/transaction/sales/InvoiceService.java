package com.urbanfurniture.accounting.transaction.sales;

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
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SalesOrderRepository salesOrderRepository;
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
                invoiceRepository.search(term, status, currentUser.contactScopeOrNull(), pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.DocumentResponse get(Long id) {
        Invoice invoice = requireInvoice(id);
        currentUser.assertCanAccessContact(invoice.getContact().getId());
        return toResponse(invoice);
    }

    /** Standalone invoice, not originating from a sales order. */
    @Transactional
    public TransactionDtos.DocumentResponse create(TransactionDtos.DocumentRequest request) {
        Contact customer = contactService.requireContact(request.contactId());
        if (!customer.canBeCustomer()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Contact '" + customer.getName() + "' is not a customer");
        }

        Invoice invoice = Invoice.builder()
                .invoiceNo(documentNumberService.next(DocumentType.INVOICE))
                .contact(customer)
                .invoiceDate(request.documentDate())
                .dueDate(request.dueDate())
                .status(DocumentStatus.DRAFT)
                .notes(request.notes())
                .build();

        for (TransactionDtos.LineRequest lr : request.lines()) {
            Product product = productService.requireProduct(lr.productId());
            BigDecimal rate = lr.taxRate() != null ? lr.taxRate() : product.getTaxRate();
            LineAmounts amounts = LineAmounts.compute(lr.quantity(), lr.unitPrice(), rate);
            invoice.addLine(InvoiceLine.builder()
                    .product(product)
                    .quantity(lr.quantity())
                    .unitPrice(Money.of(lr.unitPrice()))
                    .taxRate(rate)
                    .untaxedAmount(amounts.untaxed())
                    .taxAmount(amounts.tax())
                    .lineTotal(amounts.total())
                    .build());
        }

        recalculateTotals(invoice);
        return toResponse(invoiceRepository.save(invoice));
    }

    /**
     * Converts a confirmed sales order into a draft customer invoice, copying
     * the lines as they stood on the order.
     */
    @Transactional
    public TransactionDtos.DocumentResponse createFromSalesOrder(Long salesOrderId,
                                                                 TransactionDtos.ConvertRequest request) {
        SalesOrder order = salesOrderRepository.findWithLinesById(salesOrderId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Sales order", salesOrderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiExceptions.BusinessRuleException("A cancelled sales order cannot be invoiced");
        }
        if (order.getStatus() == OrderStatus.INVOICED) {
            throw new ApiExceptions.BusinessRuleException(
                    "Sales order " + order.getOrderNo() + " has already been invoiced");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApiExceptions.BusinessRuleException(
                    "Confirm sales order " + order.getOrderNo() + " before invoicing it");
        }

        LocalDate invoiceDate = request != null && request.documentDate() != null
                ? request.documentDate() : LocalDate.now();

        Invoice invoice = Invoice.builder()
                .invoiceNo(documentNumberService.next(DocumentType.INVOICE))
                .salesOrder(order)
                .contact(order.getContact())
                .invoiceDate(invoiceDate)
                .dueDate(request != null ? request.dueDate() : null)
                .status(DocumentStatus.DRAFT)
                .notes(order.getNotes())
                .build();

        for (SalesOrderLine ol : order.getLines()) {
            invoice.addLine(InvoiceLine.builder()
                    .product(ol.getProduct())
                    .quantity(ol.getQuantity())
                    .unitPrice(ol.getUnitPrice())
                    .taxRate(ol.getTaxRate())
                    .untaxedAmount(ol.getUntaxedAmount())
                    .taxAmount(ol.getTaxAmount())
                    .lineTotal(ol.getLineTotal())
                    .build());
        }

        recalculateTotals(invoice);
        Invoice saved = invoiceRepository.save(invoice);

        order.setStatus(OrderStatus.INVOICED);
        salesOrderRepository.save(order);

        return toResponse(saved);
    }

    /**
     * Posts the invoice to the ledger.
     * <p>
     * Double-entry mapping for a customer invoice:
     * <pre>
     *   Dr  Debtors (Accounts Receivable)   total (incl. tax)
     *       Cr  Sales Income                            untaxed
     *       Cr  Tax Payable                             tax
     * </pre>
     * Amounts come from the persisted invoice, never from the request.
     */
    @Transactional
    public TransactionDtos.DocumentResponse post(Long id) {
        Invoice invoice = requireInvoice(id);

        if (invoice.getStatus() != DocumentStatus.DRAFT) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only a draft invoice can be posted (this one is " + invoice.getStatus() + ")");
        }
        if (!Money.isPositive(invoice.getTotalAmount())) {
            throw new ApiExceptions.BusinessRuleException("Cannot post an invoice with a zero total");
        }

        JournalEntryDraft draft = JournalEntryDraft.on(
                        journalService.requireByType(JournalType.SALES),
                        invoice.getInvoiceDate(),
                        SourceType.INVOICE,
                        invoice.getId(),
                        "Customer invoice " + invoice.getInvoiceNo() + " - " + invoice.getContact().getName())
                .debit(accounts.require(SystemAccount.DEBTORS), invoice.getContact(),
                        invoice.getTotalAmount(), "Receivable from " + invoice.getContact().getName())
                .credit(accounts.require(SystemAccount.SALES_INCOME),
                        invoice.getUntaxedAmount(), "Sales - " + invoice.getInvoiceNo())
                .credit(accounts.require(SystemAccount.TAX_PAYABLE),
                        invoice.getTaxAmount(), "Output tax - " + invoice.getInvoiceNo());

        JournalEntry entry = journalPostingService.post(draft);

        invoice.setJournalEntry(entry);
        invoice.setStatus(DocumentStatus.POSTED);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public TransactionDtos.DocumentResponse cancel(Long id) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus().isPosted()) {
            throw new ApiExceptions.BusinessRuleException(
                    "A posted invoice cannot be cancelled - its ledger entry is already recorded");
        }
        invoice.setStatus(DocumentStatus.CANCELLED);
        return toResponse(invoiceRepository.save(invoice));
    }

    // ---------------- internals ----------------

    Invoice requireInvoice(Long id) {
        return invoiceRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Invoice", id));
    }

    private void recalculateTotals(Invoice invoice) {
        BigDecimal untaxed = Money.ZERO;
        BigDecimal tax = Money.ZERO;
        BigDecimal total = Money.ZERO;
        for (InvoiceLine line : invoice.getLines()) {
            untaxed = Money.add(untaxed, line.getUntaxedAmount());
            tax = Money.add(tax, line.getTaxAmount());
            total = Money.add(total, line.getLineTotal());
        }
        invoice.setUntaxedAmount(untaxed);
        invoice.setTaxAmount(tax);
        invoice.setTotalAmount(total);
    }

    TransactionDtos.DocumentResponse toResponse(Invoice invoice) {
        List<TransactionDtos.LineResponse> lines = invoice.getLines().stream()
                .map(l -> new TransactionDtos.LineResponse(
                        l.getId(), l.getLineNo(), l.getProduct().getId(), l.getProduct().getName(),
                        l.getQuantity(), l.getUnitPrice(), l.getTaxRate(),
                        l.getUntaxedAmount(), l.getTaxAmount(), l.getLineTotal()))
                .toList();

        return new TransactionDtos.DocumentResponse(
                invoice.getId(), invoice.getInvoiceNo(),
                invoice.getContact().getId(), invoice.getContact().getName(),
                invoice.getInvoiceDate(), invoice.getDueDate(), invoice.getStatus(),
                invoice.getUntaxedAmount(), invoice.getTaxAmount(), invoice.getTotalAmount(),
                invoice.getAmountPaid(), invoice.amountDue(),
                invoice.getJournalEntry() == null ? null : invoice.getJournalEntry().getId(),
                invoice.getJournalEntry() == null ? null : invoice.getJournalEntry().getEntryNo(),
                invoice.getSalesOrder() == null ? null : invoice.getSalesOrder().getId(),
                invoice.getSalesOrder() == null ? null : invoice.getSalesOrder().getOrderNo(),
                invoice.getNotes(), lines);
    }
}
