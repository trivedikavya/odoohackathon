package com.urbanfurniture.accounting.trade;

/**
 * The lifecycle of a trade, shared by both sides.
 * <p>
 * Nothing touches the ledger before {@link #INVOICED}. An order is a
 * commitment, not a transaction: agreeing to buy something does not move
 * any money, and recording it as though it did would overstate both
 * parties' books until delivery.
 */
public enum DealStatus {

    /** Buyer is still composing the request. Visible only to them. */
    RFQ_DRAFT,

    /** Sent to the supplier, awaiting a decision. */
    RFQ_SENT,

    /** Supplier declined. Terminal. */
    REJECTED,

    /** Supplier accepted. The order now exists on both sides. */
    ACCEPTED,

    /** Goods handed over. Marked manually by the supplying side. */
    DELIVERED,

    /** Invoice issued and posted. This is the first state with a ledger effect. */
    INVOICED,

    PARTIALLY_PAID,

    PAID,

    CANCELLED;

    public boolean isTerminal() {
        return this == REJECTED || this == PAID || this == CANCELLED;
    }

    /** Once invoiced, the entry exists; correct it with a reversal, not a cancel. */
    public boolean canCancel() {
        return this == RFQ_DRAFT || this == RFQ_SENT || this == ACCEPTED || this == DELIVERED;
    }

    public boolean hasLedgerEffect() {
        return this == INVOICED || this == PARTIALLY_PAID || this == PAID;
    }
}
