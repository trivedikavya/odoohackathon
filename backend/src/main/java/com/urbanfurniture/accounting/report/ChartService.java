package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.ledger.JournalLineRepository;
import com.urbanfurniture.accounting.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartService {

    private final JournalLineRepository journalLineRepository;
    private final CurrentUser currentUser;

    /**
     * Monthly income, expenses and net profit for this book.
     * <p>
     * Quiet months are filled with zeroes rather than omitted, so the
     * chart shows a flat line through them instead of silently compressing
     * the time axis and implying the business was busier than it was.
     */
    @Transactional(readOnly = true)
    public ReportDtos.SalesTrend salesTrend(Integer months) {
        Long bookId = currentUser.requireBookId();
        int window = months == null || months < 1 ? 12 : Math.min(months, 60);

        LocalDate end = LocalDate.now();
        LocalDate start = end.withDayOfMonth(1).minusMonths(window - 1L);

        List<Object[]> rows = journalLineRepository.monthlyIncomeAndExpense(bookId, start, end);

        List<ReportDtos.TrendPoint> points = new ArrayList<>();
        LocalDate cursor = start;
        LocalDate last = end.withDayOfMonth(1);

        while (!cursor.isAfter(last)) {
            String period = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
            BigDecimal income = Money.ZERO;
            BigDecimal expenses = Money.ZERO;

            for (Object[] row : rows) {
                if (period.equals(String.valueOf(row[0]))) {
                    income = Money.nullSafe((BigDecimal) row[1]);
                    expenses = Money.nullSafe((BigDecimal) row[2]);
                    break;
                }
            }

            points.add(new ReportDtos.TrendPoint(
                    period, income, expenses, Money.subtract(income, expenses)));
            cursor = cursor.plusMonths(1);
        }

        return new ReportDtos.SalesTrend(start, end, points);
    }
}
