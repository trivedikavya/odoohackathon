package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.ledger.AccountBalanceRow;
import com.urbanfurniture.accounting.ledger.JournalLineRepository;
import com.urbanfurniture.accounting.master.account.AccountType;
import com.urbanfurniture.accounting.master.account.SystemAccount;
import com.urbanfurniture.accounting.master.contact.ContactRepository;
import com.urbanfurniture.accounting.master.product.ProductRepository;
import com.urbanfurniture.accounting.transaction.purchase.BillRepository;
import com.urbanfurniture.accounting.transaction.sales.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * All financial reporting.
 * <p>
 * Every figure here is aggregated from {@code journal_line} at request time.
 * There is deliberately no summary/cache table anywhere in the system, so a
 * report can never drift out of sync with the ledger it describes.
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private final JournalLineRepository journalLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final ContactRepository contactRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ReportDtos.BalanceSheet balanceSheet(LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        List<AccountBalanceRow> rows = journalLineRepository.balancesAsOf(date);

        ReportDtos.ReportSection assets = section("Assets", rows, AccountType.ASSET);
        ReportDtos.ReportSection liabilities = section("Liabilities", rows, AccountType.LIABILITY);
        ReportDtos.ReportSection equityAccounts = section("Equity", rows, AccountType.EQUITY);

        // Retained earnings: profit accumulated in the ledger but not yet sitting
        // in a capital account. Without this the statement would not balance.
        BigDecimal totalIncome = totalFor(rows, AccountType.INCOME);
        BigDecimal totalExpenses = totalFor(rows, AccountType.EXPENSE);
        BigDecimal retainedEarnings = Money.subtract(totalIncome, totalExpenses);

        List<ReportDtos.ReportLine> equityLines = new java.util.ArrayList<>(equityAccounts.lines());
        equityLines.add(new ReportDtos.ReportLine(null, "RE", "Retained Earnings (current)",
                AccountType.EQUITY, retainedEarnings));
        BigDecimal totalEquity = Money.add(equityAccounts.total(), retainedEarnings);

        ReportDtos.ReportSection equity = new ReportDtos.ReportSection("Equity", equityLines, totalEquity);

        BigDecimal totalAssets = assets.total();
        BigDecimal totalLiabEquity = Money.add(liabilities.total(), totalEquity);
        BigDecimal difference = Money.subtract(totalAssets, totalLiabEquity);

        return new ReportDtos.BalanceSheet(
                date, assets, liabilities, equity, retainedEarnings,
                totalAssets, totalLiabEquity, difference, Money.isZero(difference));
    }

    @Transactional(readOnly = true)
    public ReportDtos.ProfitAndLoss profitAndLoss(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfYear(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

        List<AccountBalanceRow> rows = journalLineRepository.balancesBetween(start, end);

        ReportDtos.ReportSection income = section("Income", rows, AccountType.INCOME);
        ReportDtos.ReportSection expenses = section("Expenses", rows, AccountType.EXPENSE);
        BigDecimal netProfit = Money.subtract(income.total(), expenses.total());

        return new ReportDtos.ProfitAndLoss(start, end, income, expenses,
                income.total(), expenses.total(), netProfit);
    }

    @Transactional(readOnly = true)
    public ReportDtos.DashboardSummary dashboard(LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        List<AccountBalanceRow> rows = journalLineRepository.balancesAsOf(date);

        BigDecimal totalSales = totalFor(rows, AccountType.INCOME);
        BigDecimal totalPurchases = totalFor(rows, AccountType.EXPENSE);
        BigDecimal cash = naturalBalanceOf(SystemAccount.CASH, date);
        BigDecimal bank = naturalBalanceOf(SystemAccount.BANK, date);
        BigDecimal receivable = naturalBalanceOf(SystemAccount.DEBTORS, date);
        BigDecimal payable = naturalBalanceOf(SystemAccount.CREDITORS, date);

        return new ReportDtos.DashboardSummary(
                date,
                totalSales,
                totalPurchases,
                cash,
                bank,
                Money.add(cash, bank),
                receivable,
                payable,
                Money.subtract(totalSales, totalPurchases),
                invoiceRepository.count(),
                billRepository.count(),
                contactRepository.count(),
                productRepository.count());
    }

    // ---------------- internals ----------------

    private ReportDtos.ReportSection section(String title, List<AccountBalanceRow> rows, AccountType type) {
        List<ReportDtos.ReportLine> lines = rows.stream()
                .filter(r -> r.type() == type)
                .filter(r -> !Money.isZero(r.naturalBalance()))
                .map(r -> new ReportDtos.ReportLine(r.accountId(), r.code(), r.name(), r.type(),
                        r.naturalBalance()))
                .toList();

        BigDecimal total = lines.stream()
                .map(ReportDtos.ReportLine::amount)
                .reduce(Money.ZERO, Money::add);

        return new ReportDtos.ReportSection(title, lines, total);
    }

    private BigDecimal totalFor(List<AccountBalanceRow> rows, AccountType type) {
        return rows.stream()
                .filter(r -> r.type() == type)
                .map(AccountBalanceRow::naturalBalance)
                .reduce(Money.ZERO, Money::add);
    }

    /**
     * Balance of a single well-known account as at {@code asOf}, expressed on
     * its natural side so callers always get a positive number under normal
     * conditions.
     */
    private BigDecimal naturalBalanceOf(SystemAccount systemAccount, LocalDate asOf) {
        BigDecimal signedDebit = journalLineRepository.debitBalanceOf(systemAccount, asOf);
        BigDecimal value = signedDebit == null ? Money.ZERO : signedDebit;
        // Credit-normal accounts: flip the sign so a payable reads as positive.
        return switch (systemAccount) {
            case CREDITORS, TAX_PAYABLE, CAPITAL -> Money.of(value.negate());
            default -> Money.of(value);
        };
    }
}
