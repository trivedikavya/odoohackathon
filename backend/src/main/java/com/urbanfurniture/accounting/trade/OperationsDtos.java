package com.urbanfurniture.accounting.trade;

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

public final class OperationsDtos {

    private OperationsDtos() {
    }

    public record CapitalRequest(
            @NotNull PaymentMethod method,
            @NotNull LocalDate date,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 255) String note) {
    }

    public record OpeningStockRequest(
            @NotNull Long productId,
            @NotNull LocalDate date,
            @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal unitCost) {
    }

    public record ExpenseRequest(
            @NotNull Long expenseAccountId,
            @NotNull PaymentMethod method,
            @NotNull LocalDate date,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            Long analyticAccountId) {
    }

    /**
     * A trade that has already happened, recorded in one step.
     * <p>
     * The RFQ round trip exists so two registered parties can agree terms.
     * It is the wrong shape for a purchase from an offline supplier or a
     * counter sale to a walk-in customer, where there is nobody on the
     * other side to accept anything.
     */
    public record DirectTradeRequest(
            /** The counterparty. Must already be a contact of this book. */
            @NotNull Long counterpartyPartyId,
            @NotNull LocalDate date,
            LocalDate dueDate,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<TradeDtos.DealLineRequest> lines) {
    }
}
