package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.identity.PartyType;
import com.urbanfurniture.accounting.tax.TaxTreatment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TradeDtos {

    private TradeDtos() {
    }

    // ---------------- requests ----------------

    public record DealLineRequest(
            @NotBlank @Size(max = 180) String description,
            @Size(max = 20) String hsnCode,
            @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {
    }

    /**
     * Raise a request for quotation. The caller is always the buyer — you
     * ask someone to sell to you, not the other way round.
     */
    public record CreateDealRequest(
            @NotNull Long sellerPartyId,
            @NotNull LocalDate dealDate,
            LocalDate expectedDelivery,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<DealLineRequest> lines) {
    }

    public record RejectRequest(@Size(max = 500) String reason) {
    }

    public record DeliverRequest(LocalDate deliveredAt) {
    }

    public record InvoiceRequest(LocalDate docDate, LocalDate dueDate) {
    }

    public record SettlementRequest(
            @NotNull PaymentMethod method,
            @NotNull LocalDate settlementDate,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 120) String reference) {
    }

    public record TagLineRequest(@NotNull Long documentLineId, Long analyticAccountId) {
    }

    // ---------------- responses ----------------

    public record DealLineResponse(
            Long id, Integer lineNo, String description, String hsnCode,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate,
            BigDecimal untaxedAmount, BigDecimal taxAmount,
            BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount,
            BigDecimal lineTotal) {
    }

    public record DealResponse(
            Long id,
            String dealNo,
            Long buyerPartyId,
            String buyerName,
            PartyType buyerType,
            Long sellerPartyId,
            String sellerName,
            PartyType sellerType,
            Long initiatedByPartyId,
            DealStatus status,
            LocalDate dealDate,
            LocalDate expectedDelivery,
            LocalDate deliveredAt,
            String placeOfSupply,
            TaxTreatment taxTreatment,
            BigDecimal untaxedAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String notes,
            String rejectReason,
            List<DealLineResponse> lines,
            /** True when the caller is the one who must accept or reject. */
            boolean awaitingMyDecision,
            /** True when the caller is the supplying side of this deal. */
            boolean iAmSeller,
            /** Whether the counterparty keeps books, so the deal mirrors. */
            boolean mirrored,
            List<DocumentSummary> myDocuments) {
    }

    public record DocumentSummary(
            Long id, DocumentType docType, String docNo, DocumentStatus status,
            BigDecimal totalAmount, BigDecimal amountDue) {
    }

    public record DocumentLineResponse(
            Long id, Integer lineNo, String description, String hsnCode,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate,
            BigDecimal untaxedAmount, BigDecimal taxAmount,
            BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount,
            BigDecimal lineTotal,
            Long analyticAccountId, String analyticAccountName) {
    }

    public record DocumentResponse(
            Long id,
            Long dealId,
            String dealNo,
            DocumentType docType,
            String docNo,
            LocalDate docDate,
            LocalDate dueDate,
            DocumentStatus status,
            Long counterpartyPartyId,
            String counterpartyName,
            String placeOfSupply,
            TaxTreatment taxTreatment,
            BigDecimal untaxedAmount,
            BigDecimal taxAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal totalAmount,
            BigDecimal amountSettled,
            BigDecimal amountDue,
            Long journalEntryId,
            String journalEntryNo,
            List<DocumentLineResponse> lines) {
    }

    public record PaymentResponse(
            Long id, String paymentNo, Long documentId, String documentNo,
            DocumentType documentType, Long counterpartyPartyId, String counterpartyName,
            PaymentDirection direction, PaymentMethod method,
            LocalDate paymentDate, BigDecimal amount, String reference,
            Long journalEntryId, String journalEntryNo) {
    }
}
