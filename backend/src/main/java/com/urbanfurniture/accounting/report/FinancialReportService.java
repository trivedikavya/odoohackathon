package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.ledger.AccountBalanceRow;
import com.urbanfurniture.accounting.ledger.AccountType;
import com.urbanfurniture.accounting.ledger.JournalLineRepository;
import com.urbanfurniture.accounting.ledger.SystemAccount;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.trade.DealRepository;
import com.urbanfurniture.accounting.trade.DocumentType;
import com.urbanfurniture.accounting.trade.TradeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Balance sheet, P&amp;L, trial balance and dashboard — all aggregated
 * from journal lines at request time, for the signed-in book only.
 * <p>
 * There is no summary table anywhere in the schema, so these figures
 * cannot drift from the ledger they describe.
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JournalLineRepository journalLineRepository;
    private final TradeDocumentRepository documentRepository;
    private final DealRepository dealRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public ReportDtos.BalanceSheet balanceSheet(LocalDate asOf) {
        Long bookId = currentUser.requireBookId();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        List<AccountBalanceRow> rows = journalLineRepository.balancesAsOf(bookId, date);

        ReportDtos.ReportSection assets = section("Assets", rows, AccountType.ASSET);
        ReportDtos.ReportSection liabilities = section("Liabilities", rows, AccountType.LIABILITY);

        // Profit to date is equity the owner has earned but not yet
        // formally transferred. Without it the statement cannot balance,
        // and deriving it here is what avoids storing it anywhere.
        BigDecimal retained = Money.subtract(
                totalFor(rows, AccountType.INCOME), totalFor(rows, AccountType.EXPENSE));

        ReportDtos.ReportSection equityRaw = section("Equity", rows, AccountType.EQUITY);
        List<ReportDtos.ReportLine> equityLines = new ArrayList<>(equityRaw.lines());
        equityLines.add(new ReportDtos.ReportLine(
                null, "RE", "Retained Earnings (current)", AccountType.EQUITY, retained));
        ReportDtos.ReportSection equity = new ReportDtos.ReportSection(
                "Equity", equityLines, Money.add(equityRaw.total(), retained));

        BigDecimal totalAssets = assets.total();
        BigDecimal totalLiabEquity = Money.add(liabilities.total(), equity.total());
        BigDecimal difference = Money.subtract(totalAssets, totalLiabEquity);

        return new ReportDtos.BalanceSheet(date, assets, liabilities, equity, retained,
                totalAssets, totalLiabEquity, difference, Money.isZero(difference));
    }

    @Transactional(readOnly = true)
    public ReportDtos.ProfitAndLoss profitAndLoss(LocalDate from, LocalDate to) {
        Long bookId = currentUser.requireBookId();
        LocalDate start = from == null ? LocalDate.now().withDayOfYear(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

        List<AccountBalanceRow> rows = journalLineRepository.balancesBetween(bookId, start, end);
        ReportDtos.ReportSection income = section("Revenue", rows, AccountType.INCOME);

        // Cost of sales is separated from the other expenses so gross
        // margin is visible. "We sold 28 lakh that cost us 17 lakh" says
        // something "we spent 20 lakh" does not.
        ReportDtos.ReportSection costOfSales =
                section("Cost of sales", rows, AccountType.EXPENSE, true);
        ReportDtos.ReportSection expenses =
                section("Operating expenses", rows, AccountType.EXPENSE, false);

        BigDecimal grossProfit = Money.subtract(income.total(), costOfSales.total());
        BigDecimal netProfit = Money.subtract(grossProfit, expenses.total());

        BigDecimal marginPercent = Money.isZero(income.total())
                ? null
                : grossProfit.multiply(HUNDRED)
                        .divide(income.total(), 1, java.math.RoundingMode.HALF_UP);

        return new ReportDtos.ProfitAndLoss(start, end, income, costOfSales, expenses,
                income.total(), costOfSales.total(), grossProfit, marginPercent,
                expenses.total(), netProfit);
    }

    @Transactional(readOnly = true)
    public ReportDtos.TrialBalance trialBalance(LocalDate asOf) {
        Long bookId = currentUser.requireBookId();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;

        List<ReportDtos.TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = Money.ZERO;
        BigDecimal totalCredit = Money.ZERO;

        for (AccountBalanceRow row : journalLineRepository.balancesAsOf(bookId, date)) {
            BigDecimal signed = row.signedDebitBalance();
            if (Money.isZero(signed)) {
                continue;
            }
            // Each account appears on exactly one side, as its net position.
            BigDecimal debit = Money.isPositive(signed) ? signed : Money.ZERO;
            BigDecimal credit = Money.isNegative(signed) ? signed.negate() : Money.ZERO;

            rows.add(new ReportDtos.TrialBalanceRow(row.code(), row.name(), row.type(), debit, credit));
            totalDebit = Money.add(totalDebit, debit);
            totalCredit = Money.add(totalCredit, credit);
        }

        BigDecimal difference = Money.subtract(totalDebit, totalCredit);
        return new ReportDtos.TrialBalance(date, rows, totalDebit, totalCredit,
                difference, Money.isZero(difference));
    }

    @Transactional(readOnly = true)
    public ReportDtos.DashboardSummary dashboard(LocalDate asOf) {
        Book book = currentUser.requireBook();
        Long bookId = book.getId();
        Long partyId = book.getParty().getId();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;

        // One query for every balance. Fetching each control account
        // separately cost four more round trips to a remote database and
        // dominated the response time of the most-visited page.
        List<AccountBalanceRow> rows = journalLineRepository.balancesAsOf(bookId, date);

        BigDecimal cash = naturalBalanceIn(rows, SystemAccount.CASH);
        BigDecimal bank = naturalBalanceIn(rows, SystemAccount.BANK);
        BigDecimal receivable = naturalBalanceIn(rows, SystemAccount.DEBTORS);
        BigDecimal payable = naturalBalanceIn(rows, SystemAccount.CREDITORS);
        BigDecimal income = totalFor(rows, AccountType.INCOME);
        BigDecimal expenses = totalFor(rows, AccountType.EXPENSE);

        long awaiting = dealRepository.inboxForParty(partyId, PageRequest.of(0, 1)).getTotalElements();
        long openDeals = dealRepository
                .searchForParty(partyId, com.urbanfurniture.accounting.trade.DealStatus.ACCEPTED, "",
                        PageRequest.of(0, 1))
                .getTotalElements();

        return new ReportDtos.DashboardSummary(
                date, book.getName(),
                income, expenses,
                cash, bank, Money.add(cash, bank),
                receivable, payable,
                Money.subtract(income, expenses),
                documentRepository.countByBookIdAndDocType(bookId, DocumentType.INVOICE),
                documentRepository.countByBookIdAndDocType(bookId, DocumentType.BILL),
                openDeals, awaiting);
    }

    // ---------------- internals ----------------

    private ReportDtos.ReportSection section(String title, List<AccountBalanceRow> rows, AccountType type) {
        return section(title, rows, type, null);
    }

    /**
     * @param costOfSales {@code null} for every account of the type;
     *                    {@code true} for the COGS account alone;
     *                    {@code false} for every other expense. That split
     *                    is what separates gross margin from overheads.
     */
    private ReportDtos.ReportSection section(String title, List<AccountBalanceRow> rows,
                                             AccountType type, Boolean costOfSales) {
        List<ReportDtos.ReportLine> lines = rows.stream()
                .filter(r -> r.type() == type)
                .filter(r -> costOfSales == null
                        || (r.systemCode() == SystemAccount.COGS) == costOfSales)
                // A zero balance is noise, not information.
                .filter(r -> !Money.isZero(r.naturalBalance()))
                .map(r -> new ReportDtos.ReportLine(
                        r.accountId(), r.code(), r.name(), r.type(), r.naturalBalance()))
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

    private BigDecimal naturalBalanceIn(List<AccountBalanceRow> rows, SystemAccount systemAccount) {
        return rows.stream()
                .filter(r -> r.systemCode() == systemAccount)
                .map(AccountBalanceRow::naturalBalance)
                .reduce(Money.ZERO, Money::add);
    }
}
