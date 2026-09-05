package com.urbanfurniture.accounting.trade;

public enum DocumentStatus {
    /** An order: agreed, but with no ledger effect. */
    OPEN,
    POSTED,
    PARTIALLY_PAID,
    PAID,
    CANCELLED;

    public boolean isPosted() {
        return this == POSTED || this == PARTIALLY_PAID || this == PAID;
    }
}
