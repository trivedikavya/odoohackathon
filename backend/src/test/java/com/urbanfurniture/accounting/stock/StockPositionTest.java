package com.urbanfurniture.accounting.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Weighted-average costing.
 * <p>
 * The average is a quotient of two aggregates rather than a stored
 * figure, which is what lets a purchase at a new price re-cost the shelf
 * immediately and correctly. These tests pin the arithmetic, including
 * the cases where it would be tempting to divide by zero.
 */
class StockPositionTest {

    private StockPosition position(String quantity, String value) {
        return new StockPosition(new BigDecimal(quantity), new BigDecimal(value));
    }

    @Test
    @DisplayName("two receipts at different prices average out")
    void averagesAcrossReceipts() {
        // 10 @ 1000 then 10 @ 1200 -> 20 units worth 22,000
        StockPosition p = position("20", "22000.00");
        assertThat(p.averageCost()).isEqualByComparingTo("1100.00");
    }

    @ParameterizedTest(name = "{0} units worth {1} -> average {2}")
    @CsvSource({
            "10,   10000.00, 1000.00",
            "20,   22000.00, 1100.00",
            "3,     1000.00,  333.33",   // rounds to the paisa
            "7,      100.00,   14.29",
            "1,        0.01,    0.01"
    })
    void computesAverageToThePaisa(String quantity, String value, String expected) {
        assertThat(position(quantity, value).averageCost()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("an empty shelf has no average, and does not divide by zero")
    void emptyStockHasZeroAverage() {
        // There is no meaningful average of nothing. Returning zero keeps
        // callers from having to guard a division they cannot perform.
        assertThat(position("0", "0.00").averageCost()).isEqualByComparingTo("0.00");
        assertThat(StockPosition.EMPTY.averageCost()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a negative position reports no average rather than a negative one")
    void negativeQuantityHasZeroAverage() {
        assertThat(position("-5", "100.00").averageCost()).isEqualByComparingTo("0.00");
    }

    @Test
    void canCoverOnlyWhenEnoughIsOnHand() {
        StockPosition p = position("10", "10000.00");

        assertThat(p.canCover(new BigDecimal("10"))).isTrue();
        assertThat(p.canCover(new BigDecimal("9.5"))).isTrue();
        assertThat(p.canCover(new BigDecimal("10.001"))).isFalse();
    }

    @Test
    @DisplayName("the shortfall is what the error message quotes back")
    void reportsTheShortfall() {
        StockPosition p = position("3", "300.00");

        assertThat(p.shortfall(new BigDecimal("10"))).isEqualByComparingTo("7");
        // Never negative: having spare stock is not a shortfall of -5.
        assertThat(p.shortfall(new BigDecimal("1"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("selling everything leaves a shelf worth nothing")
    void sellingOutLeavesNothing() {
        // 20 in at 22,000; 20 out at the 1,100 average -> both net to zero,
        // which is what keeps the Inventory account at zero too.
        StockPosition p = position("0", "0.00");
        assertThat(p.quantity()).isEqualByComparingTo("0");
        assertThat(p.value()).isEqualByComparingTo("0.00");
    }
}
