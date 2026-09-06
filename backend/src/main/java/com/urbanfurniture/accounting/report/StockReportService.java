package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.ledger.AccountBalanceRow;
import com.urbanfurniture.accounting.ledger.JournalLineRepository;
import com.urbanfurniture.accounting.ledger.SystemAccount;
import com.urbanfurniture.accounting.master.ProductType;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.stock.StockMovementRepository;
import com.urbanfurniture.accounting.stock.StockPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What is on the shelf, what it is worth, and whether the general ledger
 * agrees.
 * <p>
 * The tie-out is the point of this report. Stock is counted in one place
 * (the stock ledger) and valued in another (the Inventory account), and
 * the two are written by different code paths. Publishing the comparison
 * turns "inventory is tracked" from a claim into something a reader can
 * check, in the same way the balance sheet publishes its own difference.
 */
@Service
@RequiredArgsConstructor
public class StockReportService {

    private final StockMovementRepository stockMovements;
    private final JournalLineRepository journalLineRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public ReportDtos.StockLedger stockLedger(LocalDate asOf) {
        Long bookId = currentUser.requireBookId();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;

        List<ReportDtos.StockRow> rows = new ArrayList<>();
        BigDecimal totalValue = Money.ZERO;

        for (Object[] row : stockMovements.positionsAsOf(bookId, date)) {
            BigDecimal quantity = (BigDecimal) row[3];
            BigDecimal value = Money.nullSafe((BigDecimal) row[4]);

            // An item bought and entirely sold nets to zero. Listing it
            // would bury the things actually on the shelf.
            if (quantity.signum() == 0 && Money.isZero(value)) {
                continue;
            }

            StockPosition position = new StockPosition(quantity, value);
            rows.add(new ReportDtos.StockRow(
                    (Long) row[0], (String) row[1], (ProductType) row[2],
                    quantity, position.averageCost(), value));

            totalValue = Money.add(totalValue, value);
        }

        // The Inventory account, straight from the general ledger.
        BigDecimal ledgerValue = journalLineRepository.balancesAsOf(bookId, date).stream()
                .filter(r -> r.systemCode() == SystemAccount.INVENTORY)
                .map(AccountBalanceRow::naturalBalance)
                .reduce(Money.ZERO, Money::add);

        BigDecimal difference = Money.subtract(totalValue, ledgerValue);

        return new ReportDtos.StockLedger(
                date, rows, totalValue, ledgerValue, difference, Money.isZero(difference));
    }
}
