package com.urbanfurniture.accounting.transaction;

/**
 * Lifecycle of an invoice or a vendor bill.
 * <p>
 * A document only hits the ledger when it moves to {@link #POSTED}; DRAFT
 * documents are editable and have no accounting effect.
 */
public enum DocumentStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID,
    CANCELLED;

    public boolean isPosted() {
        return this == POSTED || this == PARTIALLY_PAID || this == PAID;
    }

    public boolean isPayable() {
        return this == POSTED || this == PARTIALLY_PAID;
    }
}
