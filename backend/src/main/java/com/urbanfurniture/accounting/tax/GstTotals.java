package com.urbanfurniture.accounting.tax;

import com.urbanfurniture.accounting.common.Money;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.function.Function;

/**
 * A whole document's tax, decomposed into the accounts it must post to.
 * <p>
 * The {@code unsplit} component is the load-bearing part of this design.
 * It is the residual - whatever the document's total tax is minus the
 * three GST components actually recorded on its lines - and it posts to
 * the undifferentiated tax account.
 * <p>
 * Computing it as a residual rather than asserting the components add up
 * means the posting can never unbalance, whatever the lines contain:
 * legacy rows written before GST existed, a document raised while the
 * company state was unset, or a future mix of taxable and exempt lines
 * all post correctly, because {@code cgst + sgst + igst + unsplit} is
 * equal to the document's tax total by construction rather than by
 * assumption.
 */
public record GstTotals(BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal unsplit) {

    /**
     * Aggregates the GST components across a document's lines and derives
     * the residual against the document's own tax total.
     *
     * @param totalTax the document header's tax figure - the authority
     * @param lines    the document's lines
     */
    public static <L> GstTotals from(BigDecimal totalTax,
                                     Collection<L> lines,
                                     Function<L, BigDecimal> cgstOf,
                                     Function<L, BigDecimal> sgstOf,
                                     Function<L, BigDecimal> igstOf) {
        BigDecimal cgst = sum(lines, cgstOf);
        BigDecimal sgst = sum(lines, sgstOf);
        BigDecimal igst = sum(lines, igstOf);

        BigDecimal accounted = Money.add(Money.add(cgst, sgst), igst);
        BigDecimal unsplit = Money.subtract(Money.nullSafe(totalTax), accounted);

        return new GstTotals(cgst, sgst, igst, unsplit);
    }

    /** The treatment implied by which components are actually present. */
    public TaxTreatment treatment() {
        if (Money.isPositive(igst)) {
            return TaxTreatment.INTER_STATE;
        }
        if (Money.isPositive(cgst) || Money.isPositive(sgst)) {
            return TaxTreatment.INTRA_STATE;
        }
        return TaxTreatment.UNSPECIFIED;
    }

    private static <L> BigDecimal sum(Collection<L> lines, Function<L, BigDecimal> extractor) {
        return lines.stream()
                .map(extractor)
                .map(Money::nullSafe)
                .reduce(Money.ZERO, Money::add);
    }
}
