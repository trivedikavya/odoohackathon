package com.urbanfurniture.accounting.analytic;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AnalyticDtos {

    private AnalyticDtos() {
    }

    public record AnalyticAccountRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull AnalyticAccountType type,
            @Size(max = 500) String notes) {
    }

    public record AnalyticAccountResponse(
            Long id, String code, String name, AnalyticAccountType type,
            String notes, Boolean active) {
    }

    public record BudgetRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull Long analyticAccountId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal plannedAmount,
            @Size(max = 150) String responsible,
            @Size(max = 500) String notes) {
    }

    public record BudgetResponse(
            Long id, String name, Long analyticAccountId, String analyticAccountCode,
            String analyticAccountName, LocalDate periodStart, LocalDate periodEnd,
            BigDecimal plannedAmount, String responsible, String notes, Boolean active) {
    }
}
