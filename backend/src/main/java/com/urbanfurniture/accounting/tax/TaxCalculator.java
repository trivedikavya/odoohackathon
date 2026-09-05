package com.urbanfurniture.accounting.tax;

import com.urbanfurniture.accounting.common.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Splits a line's tax into its GST components.
 * <p>
 * Deliberately a pure static utility with no Spring dependency: the rules
 * here are arithmetic and jurisdictional, not stateful, and keeping them
 * free of injection makes them exhaustively unit-testable without a
 * context.
 */
public final class TaxCalculator {

    private static final BigDecimal TWO = new BigDecimal("2");

    private TaxCalculator() {
    }

    /**
     * Decides the treatment by comparing the place of supply against the
     * seller's registered state.
     * <p>
     * Comparison is on a normalised form because these are free-text
     * fields typed by humans - "Gujarat", "gujarat " and "GUJARAT" are
     * the same state, and treating them as different would wrongly turn
     * an intra-state sale into an inter-state one.
     */
    public static TaxTreatment treatmentFor(String companyState, String placeOfSupply) {
        String seller = normalizeState(companyState);
        String buyer = normalizeState(placeOfSupply);
        if (seller.isEmpty() || buyer.isEmpty()) {
            return TaxTreatment.UNSPECIFIED;
        }
        return seller.equals(buyer) ? TaxTreatment.INTRA_STATE : TaxTreatment.INTER_STATE;
    }

    /**
     * Splits {@code taxAmount} according to {@code treatment}.
     * <p>
     * For an intra-state supply the halves are computed as
     * {@code cgst = round(tax / 2)} and {@code sgst = tax - cgst} rather
     * than rounding both halves independently. On an odd number of paise
     * - a ₹0.01 tax, or 18% of ₹52.83 - rounding both halves would
     * produce a total one paisa away from the line's tax and unbalance
     * the journal entry. Deriving the second half by subtraction makes
     * the components sum to the original exactly, by construction.
     */
    public static GstSplit split(BigDecimal taxAmount, TaxTreatment treatment) {
        BigDecimal tax = Money.nullSafe(taxAmount);

        if (treatment == null || treatment == TaxTreatment.UNSPECIFIED || Money.isZero(tax)) {
            return new GstSplit(Money.ZERO, Money.ZERO, Money.ZERO,
                    treatment == null ? TaxTreatment.UNSPECIFIED : treatment);
        }

        if (treatment == TaxTreatment.INTER_STATE) {
            return new GstSplit(Money.ZERO, Money.ZERO, Money.of(tax), TaxTreatment.INTER_STATE);
        }

        BigDecimal cgst = tax.divide(TWO, Money.SCALE, RoundingMode.HALF_UP);
        BigDecimal sgst = Money.subtract(tax, cgst);
        return new GstSplit(cgst, sgst, Money.ZERO, TaxTreatment.INTRA_STATE);
    }

    /** Convenience: resolve the treatment and split in one step. */
    public static GstSplit split(BigDecimal taxAmount, String companyState, String placeOfSupply) {
        return split(taxAmount, treatmentFor(companyState, placeOfSupply));
    }

    /**
     * Folds a free-text state name into a comparable key: case-insensitive,
     * trimmed, with internal runs of whitespace and punctuation collapsed
     * so "Tamil  Nadu" and "Tamil Nadu" match.
     */
    private static String normalizeState(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
