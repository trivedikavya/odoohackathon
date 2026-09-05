package com.urbanfurniture.accounting.tax;

import com.urbanfurniture.accounting.common.Money;

import java.math.BigDecimal;

/**
 * One line's tax amount decomposed into its GST components.
 * <p>
 * Invariant, guaranteed by {@link TaxCalculator}: {@code cgst + sgst + igst}
 * equals the line's total tax exactly, to the paisa. The posting engine
 * relies on this - it credits these three figures in place of the single
 * tax figure, so any rounding drift here would surface as an unbalanced
 * journal entry rather than a quietly wrong tax return.
 */
public record GstSplit(BigDecimal cgst, BigDecimal sgst, BigDecimal igst, TaxTreatment treatment) {

    public static final GstSplit NONE =
            new GstSplit(Money.ZERO, Money.ZERO, Money.ZERO, TaxTreatment.UNSPECIFIED);

    /** Total tax represented by this split. */
    public BigDecimal total() {
        return Money.add(Money.add(cgst, sgst), igst);
    }

    /**
     * True when the tax could not be attributed to a GST component and must
     * fall back to the undifferentiated tax account.
     */
    public boolean isUnsplit() {
        return treatment == TaxTreatment.UNSPECIFIED;
    }
}
