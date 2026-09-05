package com.urbanfurniture.accounting.tax;

/**
 * How a supply is taxed under GST, decided by comparing the place of
 * supply against the seller's own registered state.
 */
public enum TaxTreatment {

    /** Buyer and seller in the same state: tax splits into CGST + SGST. */
    INTRA_STATE,

    /** Buyer and seller in different states: a single IGST at the full rate. */
    INTER_STATE,

    /**
     * The place of supply or the company's own state is unknown, so the
     * split cannot be determined honestly.
     * <p>
     * We deliberately do <em>not</em> guess here. Guessing "probably
     * intra-state" would produce a return that under-reports IGST and
     * over-reports SGST to a state government that was never owed it -
     * a filing error that is materially worse than an unsplit figure.
     * Instead the tax posts to the undifferentiated tax account, which is
     * visibly incomplete and therefore gets fixed.
     */
    UNSPECIFIED
}
