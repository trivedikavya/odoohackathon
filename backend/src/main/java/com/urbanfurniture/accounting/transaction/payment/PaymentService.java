package com.urbanfurniture.accounting.transaction.payment;

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
import com.urbanfurniture.accounting.master.journal.JournalService;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.transaction.DocumentStatus;
import com.urbanfurniture.accounting.transaction.TransactionDtos;
import com.urbanfurniture.accounting.transaction.purchase.Bill;
import com.urbanfurniture.accounting.transaction.purchase.BillRepository;
import com.urbanfurniture.accounting.transaction.sales.Invoice;
import com.urbanfurniture.accounting.transaction.sales.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final DocumentNumberService documentNumberService;
    private final JournalPostingService journalPostingService;
    private final JournalService journalService;
    private final AccountLookup accounts;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResponse<TransactionDtos.PaymentResponse> search(String search, PaymentDirection direction,
                                                                Pageable pageable) {
        String term = SearchTerms.normalize(search);
        return PageResponse.of(
                paymentRepository.search(term, direction, currentUser.contactScopeOrNull(), pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.PaymentResponse get(Long id) {
        Payment payment = paymentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Payment", id));
        currentUser.assertCanAccessContact(payment.getContact().getId());
        return toResponse(payment);
    }

    /**
     * Records money received against a customer invoice.
     * <p>
     * Double-entry mapping:
     * <pre>
     *   Dr  Cash / Bank                     amount
     *       Cr  Debtors (Accounts Receivable)       amount
     * </pre>
     */
    @Transactional
    public TransactionDtos.PaymentResponse payInvoice(Long invoiceId, TransactionDtos.PaymentRequest request) {
        Invoice invoice = invoiceRepository.findWithLinesById(invoiceId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Invoice", invoiceId));

        // A portal user may only pay their own invoices.
        currentUser.assertCanAccessContact(invoice.getContact().getId());

        BigDecimal amount = Money.of(request.amount());
        assertPayable(invoice.getStatus(), invoice.amountDue(), amount, "invoice", invoice.getInvoiceNo());

        Payment payment = Payment.builder()
                .paymentNo(documentNumberService.next(DocumentType.PAYMENT))
                .contact(invoice.getContact())
                .direction(PaymentDirection.RECEIVE)
                .method(request.method())
                .paymentDate(request.paymentDate())
                .amount(amount)
                .invoice(invoice)
                .reference(request.reference())
                .reconciled(false)
                .build();

        JournalEntryDraft draft = JournalEntryDraft.on(
                        journalService.requireByType(request.method().journalType()),
                        request.paymentDate(),
                        SourceType.PAYMENT,
                        null,
                        "Payment received for " + invoice.getInvoiceNo()
                                + " - " + invoice.getContact().getName())
                .debit(accounts.require(request.method().account()), amount,
                        request.method() + " received")
                .credit(accounts.require(SystemAccount.DEBTORS), invoice.getContact(), amount,
                        "Settles " + invoice.getInvoiceNo());

        JournalEntry entry = journalPostingService.post(draft);
        payment.setJournalEntry(entry);
        Payment saved = paymentRepository.save(payment);

        // Backfill source_id now that the payment has an identity.
        entry.setSourceId(saved.getId());

        invoice.setAmountPaid(Money.add(invoice.getAmountPaid(), amount));
        invoice.setStatus(Money.isPositive(invoice.amountDue())
                ? DocumentStatus.PARTIALLY_PAID
                : DocumentStatus.PAID);
        invoiceRepository.save(invoice);

        return toResponse(saved);
    }

    /**
     * Records money paid against a vendor bill.
     * <p>
     * Double-entry mapping:
     * <pre>
     *   Dr  Creditors (Accounts Payable)    amount
     *       Cr  Cash / Bank                          amount
     * </pre>
     */
    @Transactional
    public TransactionDtos.PaymentResponse payBill(Long billId, TransactionDtos.PaymentRequest request) {
        Bill bill = billRepository.findWithLinesById(billId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Bill", billId));

        currentUser.assertCanAccessContact(bill.getContact().getId());

        BigDecimal amount = Money.of(request.amount());
        assertPayable(bill.getStatus(), bill.amountDue(), amount, "bill", bill.getBillNo());

        Payment payment = Payment.builder()
                .paymentNo(documentNumberService.next(DocumentType.PAYMENT))
                .contact(bill.getContact())
                .direction(PaymentDirection.PAY)
                .method(request.method())
                .paymentDate(request.paymentDate())
                .amount(amount)
                .bill(bill)
                .reference(request.reference())
                .reconciled(false)
                .build();

        JournalEntryDraft draft = JournalEntryDraft.on(
                        journalService.requireByType(request.method().journalType()),
                        request.paymentDate(),
                        SourceType.PAYMENT,
                        null,
                        "Payment made for " + bill.getBillNo() + " - " + bill.getContact().getName())
                .debit(accounts.require(SystemAccount.CREDITORS), bill.getContact(), amount,
                        "Settles " + bill.getBillNo())
                .credit(accounts.require(request.method().account()), amount,
                        request.method() + " paid out");

        JournalEntry entry = journalPostingService.post(draft);
        payment.setJournalEntry(entry);
        Payment saved = paymentRepository.save(payment);

        entry.setSourceId(saved.getId());

        bill.setAmountPaid(Money.add(bill.getAmountPaid(), amount));
        bill.setStatus(Money.isPositive(bill.amountDue())
                ? DocumentStatus.PARTIALLY_PAID
                : DocumentStatus.PAID);
        billRepository.save(bill);

        return toResponse(saved);
    }

    // ---------------- internals ----------------

    /** Refuses payments against unposted documents and refuses overpayment. */
    private void assertPayable(DocumentStatus status, BigDecimal amountDue, BigDecimal amount,
                               String documentKind, String documentNo) {
        if (!status.isPayable()) {
            throw new ApiExceptions.BusinessRuleException(
                    "The " + documentKind + " " + documentNo + " is " + status
                            + " and cannot accept a payment");
        }
        if (!Money.isPositive(amount)) {
            throw new ApiExceptions.BusinessRuleException("Payment amount must be greater than zero");
        }
        if (Money.gt(amount, amountDue)) {
            throw new ApiExceptions.BusinessRuleException(
                    "Payment of " + amount + " exceeds the outstanding balance of " + amountDue
                            + " on " + documentKind + " " + documentNo);
        }
    }

    TransactionDtos.PaymentResponse toResponse(Payment p) {
        return new TransactionDtos.PaymentResponse(
                p.getId(), p.getPaymentNo(), p.getContact().getId(), p.getContact().getName(),
                p.getDirection(), p.getMethod(), p.getPaymentDate(), p.getAmount(),
                p.getInvoice() == null ? null : p.getInvoice().getId(),
                p.getInvoice() == null ? null : p.getInvoice().getInvoiceNo(),
                p.getBill() == null ? null : p.getBill().getId(),
                p.getBill() == null ? null : p.getBill().getBillNo(),
                p.getJournalEntry() == null ? null : p.getJournalEntry().getId(),
                p.getJournalEntry() == null ? null : p.getJournalEntry().getEntryNo(),
                p.getReference(), p.getReconciled());
    }
}
