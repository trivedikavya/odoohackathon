package com.urbanfurniture.accounting.transaction;

import com.urbanfurniture.accounting.common.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computed amounts for one order/invoice/bill line.
 * <p>
 * Always derived server-side from quantity, unit price and tax rate - line
 * totals are never accepted from the client, so a tampered payload cannot
 * produce an invoice whose total disagrees with its lines.
 */
public record LineAmounts(BigDecimal untaxed, BigDecimal tax, BigDecimal total) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static LineAmounts compute(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        BigDecimal rate = taxRate == null ? BigDecimal.ZERO : taxRate;

        BigDecimal untaxed = qty.multiply(price).setScale(Money.SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = untaxed.multiply(rate)
                .divide(HUNDRED, Money.SCALE, RoundingMode.HALF_UP);

        return new LineAmounts(untaxed, tax, Money.add(untaxed, tax));
    }
}
