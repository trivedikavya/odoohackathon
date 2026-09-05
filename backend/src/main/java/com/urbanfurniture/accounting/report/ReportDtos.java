package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.master.account.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** One account's contribution to a report section. */
    public record ReportLine(
            Long accountId,
            String code,
            String name,
            AccountType type,
            BigDecimal amount) {
    }

    public record ReportSection(
            String title,
            List<ReportLine> lines,
            BigDecimal total) {
    }

    /**
     * Assets = Liabilities + Equity.
     * <p>
     * Equity includes retained earnings (income - expenses to date), which is
     * what makes the statement balance without any stored figure anywhere.
     */
    public record BalanceSheet(
            LocalDate asOf,
            ReportSection assets,
            ReportSection liabilities,
            ReportSection equity,
            BigDecimal retainedEarnings,
            BigDecimal totalAssets,
            BigDecimal totalLiabilitiesAndEquity,
            /** Must be zero. Surfaced so the imbalance is visible rather than hidden. */
            BigDecimal difference,
            boolean balanced) {
    }

    public record ProfitAndLoss(
            LocalDate from,
            LocalDate to,
            ReportSection income,
            ReportSection expenses,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netProfit) {
    }

    public record DashboardSummary(
            LocalDate asOf,
            BigDecimal totalSales,
            BigDecimal totalPurchases,
            BigDecimal cashBalance,
            BigDecimal bankBalance,
            BigDecimal cashAndBank,
            BigDecimal accountsReceivable,
            BigDecimal accountsPayable,
            BigDecimal netProfit,
            long invoiceCount,
            long billCount,
            long contactCount,
            long productCount) {
    }
}
