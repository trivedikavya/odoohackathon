package com.urbanfurniture.accounting.transaction;

import com.urbanfurniture.accounting.transaction.payment.PaymentDirection;
import com.urbanfurniture.accounting.transaction.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request/response shapes shared by the sales and purchase flows.
 * <p>
 * Note that requests carry only quantity, unit price and (optionally) tax rate.
 * All totals are computed server-side.
 */
public final class TransactionDtos {

    private TransactionDtos() {
    }

    // ---------------- requests ----------------

    public record LineRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
            /** Optional override; defaults to the product's configured rate. */
            @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {
    }

    public record OrderRequest(
            @NotNull Long contactId,
            @NotNull LocalDate orderDate,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<LineRequest> lines) {
    }

    public record DocumentRequest(
            @NotNull Long contactId,
            @NotNull LocalDate documentDate,
            LocalDate dueDate,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<LineRequest> lines) {
    }

    public record ConvertRequest(
            LocalDate documentDate,
            LocalDate dueDate) {
    }

    public record PaymentRequest(
            @NotNull PaymentMethod method,
            @NotNull LocalDate paymentDate,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 120) String reference) {
    }

    // ---------------- responses ----------------

    public record LineResponse(
            Long id,
            Integer lineNo,
            Long productId,
            String productName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal untaxedAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
    }

    public record OrderResponse(
            Long id,
            String orderNo,
            Long contactId,
            String contactName,
            LocalDate orderDate,
            OrderStatus status,
            BigDecimal untaxedAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String notes,
            List<LineResponse> lines,
            /** Populated once the order has been converted. */
            Long generatedDocumentId,
            String generatedDocumentNo) {
    }

    public record DocumentResponse(
            Long id,
            String documentNo,
            Long contactId,
            String contactName,
            LocalDate documentDate,
            LocalDate dueDate,
            DocumentStatus status,
            BigDecimal untaxedAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal amountDue,
            Long journalEntryId,
            String journalEntryNo,
            Long sourceOrderId,
            String sourceOrderNo,
            String notes,
            List<LineResponse> lines) {
    }

    public record PaymentResponse(
            Long id,
            String paymentNo,
            Long contactId,
            String contactName,
            PaymentDirection direction,
            PaymentMethod method,
            LocalDate paymentDate,
            BigDecimal amount,
            Long invoiceId,
            String invoiceNo,
            Long billId,
            String billNo,
            Long journalEntryId,
            String journalEntryNo,
            String reference,
            Boolean reconciled) {
    }
}
