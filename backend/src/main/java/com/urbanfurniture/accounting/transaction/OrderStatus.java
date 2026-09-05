package com.urbanfurniture.accounting.transaction;

/**
 * Lifecycle of a sales or purchase order.
 * <p>
 * Orders are commercial documents with no accounting effect - nothing reaches
 * the ledger until the order is converted into an invoice or a bill.
 * {@code INVOICED} is used by sales orders, {@code BILLED} by purchase orders.
 */
public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    INVOICED,
    BILLED,
    CANCELLED
}
