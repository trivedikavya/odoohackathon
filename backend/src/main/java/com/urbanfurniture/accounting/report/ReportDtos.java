package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.ledger.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportLine(Long accountId, String code, String name, AccountType type, BigDecimal amount) {
    }

    public record ReportSection(String title, List<ReportLine> lines, BigDecimal total) {
    }

    /** Assets = Liabilities + Equity. The difference is published, not hidden. */
    public record BalanceSheet(
            LocalDate asOf,
            ReportSection assets,
            ReportSection liabilities,
            ReportSection equity,
            BigDecimal retainedEarnings,
            BigDecimal totalAssets,
            BigDecimal totalLiabilitiesAndEquity,
            BigDecimal difference,
            boolean balanced) {
    }

    /**
     * Revenue less the cost of what was sold, then less overheads.
     * <p>
     * Splitting cost of sales out of the other expenses is what makes
     * gross margin visible - "we sold 28 lakh of furniture that cost us
     * 17 lakh to buy" is a different and more useful statement than "we
     * spent 20 lakh".
     */
    public record ProfitAndLoss(
            LocalDate from, LocalDate to,
            ReportSection income,
            ReportSection costOfSales,
            ReportSection expenses,
            BigDecimal totalIncome,
            BigDecimal totalCostOfSales,
            BigDecimal grossProfit,
            /** Gross profit as a percentage of revenue; null when there was none. */
            BigDecimal grossMarginPercent,
            BigDecimal totalExpenses,
            BigDecimal netProfit) {
    }

    // ---------------- stock ----------------

    public record StockRow(
            Long productId, String productName, com.urbanfurniture.accounting.master.ProductType type,
            BigDecimal quantityOnHand, BigDecimal averageCost, BigDecimal value) {
    }

    /**
     * Stock on hand, cross-checked against the Inventory account.
     * <p>
     * The two are written by different code paths, so publishing the
     * comparison turns "inventory is tracked" into something a reader can
     * verify rather than take on trust.
     */
    public record StockLedger(
            LocalDate asOf,
            List<StockRow> rows,
            BigDecimal totalValue,
            BigDecimal ledgerBalance,
            BigDecimal difference,
            boolean reconciled) {
    }

    public record TopProduct(
            Long productId, String productName,
            BigDecimal unitsSold, BigDecimal revenue, BigDecimal cost,
            BigDecimal margin, BigDecimal marginPercent) {
    }

    /** Every account's debit and credit, proving the ledger sums to zero. */
    public record TrialBalanceRow(
            String code, String name, AccountType type, BigDecimal debit, BigDecimal credit) {
    }

    public record TrialBalance(
            LocalDate asOf,
            List<TrialBalanceRow> rows,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal difference,
            boolean balanced) {
    }

    public record DashboardSummary(
            LocalDate asOf,
            String bookName,
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
            long openDealCount,
            long awaitingMyDecisionCount) {
    }

    // ---------------- aging ----------------

    public enum AgingType {
        /** Money owed to us: the Accounts Receivable control account. */
        RECEIVABLE,
        /** Money we owe: the Accounts Payable control account. */
        PAYABLE
    }

    public record AgingDocument(
            Long documentId, String documentNo, LocalDate documentDate, LocalDate dueDate,
            long daysOverdue, BigDecimal amountDue, String bucket) {
    }

    public record AgingRow(
            Long partyId, String partyName,
            BigDecimal current, BigDecimal days1To30, BigDecimal days31To60,
            BigDecimal days61To90, BigDecimal days90Plus, BigDecimal total,
            List<AgingDocument> documents) {
    }

    /**
     * Aging is necessarily derived from documents, because due dates live
     * on invoices and never reach a journal line. The total is therefore
     * cross-checked against the ledger's control account and the result
     * published, rather than assumed.
     */
    public record AgingReport(
            LocalDate asOf, AgingType type, List<AgingRow> rows,
            BigDecimal current, BigDecimal days1To30, BigDecimal days31To60,
            BigDecimal days61To90, BigDecimal days90Plus, BigDecimal total,
            BigDecimal ledgerBalance, BigDecimal difference, boolean reconciled) {
    }

    // ---------------- counterparty reconciliation ----------------

    /**
     * One counterparty's two-sided position.
     * <p>
     * When both parties keep books on this platform, what we say they owe
     * us must equal what they say they owe us. This is the check that
     * proves the mirroring is sound; a mismatch means the two ledgers have
     * diverged, which should be impossible.
     */
    public record ReconciliationRow(
            Long partyId,
            String partyName,
            /** What our books say. Positive means they owe us. */
            BigDecimal ourPosition,
            /** What their books say about us, sign-aligned for comparison. */
            BigDecimal theirPosition,
            BigDecimal difference,
            boolean matched,
            /** False when the counterparty keeps no books, so there is nothing to compare. */
            boolean comparable) {
    }

    public record ReconciliationReport(
            LocalDate asOf,
            List<ReconciliationRow> rows,
            int comparableCount,
            int matchedCount,
            boolean allMatched) {
    }

    // ---------------- budgets ----------------

    public record BudgetPerformance(
            Long budgetId, String name,
            String analyticAccountCode, String analyticAccountName,
            LocalDate periodStart, LocalDate periodEnd, String responsible,
            BigDecimal plannedAmount,
            /** Aggregated from journal lines; never stored. */
            BigDecimal actualAmount,
            BigDecimal variance,
            BigDecimal utilisationPercent,
            boolean overBudget) {
    }

    public record BudgetReport(
            LocalDate asOf, List<BudgetPerformance> budgets,
            BigDecimal totalPlanned, BigDecimal totalActual, BigDecimal totalVariance) {
    }

    // ---------------- charts ----------------

    public record TrendPoint(String period, BigDecimal income, BigDecimal expenses, BigDecimal netProfit) {
    }

    public record SalesTrend(LocalDate from, LocalDate to, List<TrendPoint> points) {
    }
}
