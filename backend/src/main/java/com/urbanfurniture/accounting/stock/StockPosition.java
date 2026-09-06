package com.urbanfurniture.accounting.stock;

import com.urbanfurniture.accounting.common.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What is on hand for one product, and what it is worth.
 * <p>
 * The average cost is the quotient of the two, computed here rather than
 * stored anywhere — which is why a purchase at a new price immediately
 * and correctly re-averages everything without a rebuild step.
 */
public record StockPosition(BigDecimal quantity, BigDecimal value) {

    public static final StockPosition EMPTY = new StockPosition(BigDecimal.ZERO, Money.ZERO);

    /**
     * Weighted average cost per unit.
     * <p>
     * Zero when nothing is on hand: there is no meaningful average of an
     * empty set, and returning zero keeps callers from having to
     * special-case a division they cannot perform.
     */
    public BigDecimal averageCost() {
        if (quantity == null || quantity.signum() <= 0) {
            return Money.ZERO;
        }
        return value.divide(quantity, Money.SCALE, RoundingMode.HALF_UP);
    }

    public boolean canCover(BigDecimal required) {
        return quantity != null && required != null && quantity.compareTo(required) >= 0;
    }

    public BigDecimal shortfall(BigDecimal required) {
        BigDecimal have = quantity == null ? BigDecimal.ZERO : quantity;
        return required.subtract(have).max(BigDecimal.ZERO);
    }
}
