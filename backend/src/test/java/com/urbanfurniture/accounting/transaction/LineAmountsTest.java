package com.urbanfurniture.accounting.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LineAmountsTest {

    @Test
    @DisplayName("computes untaxed, tax and total for a taxed line")
    void computesTaxedLine() {
        LineAmounts a = LineAmounts.compute(new BigDecimal("3"), new BigDecimal("25000.00"), new BigDecimal("18.00"));

        assertThat(a.untaxed()).isEqualByComparingTo("75000.00");
        assertThat(a.tax()).isEqualByComparingTo("13500.00");
        assertThat(a.total()).isEqualByComparingTo("88500.00");
    }

    @Test
    @DisplayName("a zero tax rate produces no tax component")
    void computesUntaxedLine() {
        LineAmounts a = LineAmounts.compute(new BigDecimal("4"), new BigDecimal("15000.00"), BigDecimal.ZERO);

        assertThat(a.untaxed()).isEqualByComparingTo("60000.00");
        assertThat(a.tax()).isEqualByComparingTo("0.00");
        assertThat(a.total()).isEqualByComparingTo("60000.00");
    }

    @Test
    @DisplayName("total always equals untaxed plus tax after rounding")
    void totalIsConsistentAfterRounding() {
        // 3 x 33.33 @ 5% -> 99.99 + 5.00 (4.9995 rounded half-up)
        LineAmounts a = LineAmounts.compute(new BigDecimal("3"), new BigDecimal("33.33"), new BigDecimal("5.00"));

        assertThat(a.untaxed()).isEqualByComparingTo("99.99");
        assertThat(a.tax()).isEqualByComparingTo("5.00");
        assertThat(a.total()).isEqualByComparingTo(a.untaxed().add(a.tax()));
    }

    @Test
    @DisplayName("fractional quantities are supported")
    void supportsFractionalQuantities() {
        LineAmounts a = LineAmounts.compute(new BigDecimal("2.500"), new BigDecimal("120.00"), BigDecimal.ZERO);

        assertThat(a.untaxed()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("null inputs are treated as zero rather than throwing")
    void handlesNulls() {
        LineAmounts a = LineAmounts.compute(null, null, null);

        assertThat(a.untaxed()).isEqualByComparingTo("0.00");
        assertThat(a.tax()).isEqualByComparingTo("0.00");
        assertThat(a.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("amounts are scaled to two decimal places")
    void scalesToTwoDecimals() {
        LineAmounts a = LineAmounts.compute(new BigDecimal("1"), new BigDecimal("10.555"), BigDecimal.ZERO);

        assertThat(a.untaxed().scale()).isEqualTo(2);
        assertThat(a.untaxed()).isEqualByComparingTo("10.56");
    }
}
