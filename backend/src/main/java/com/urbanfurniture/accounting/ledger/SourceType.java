package com.urbanfurniture.accounting.ledger;

/** What produced a journal entry. */
public enum SourceType {
    INVOICE,
    BILL,
    PAYMENT,
    /** Cost of goods, booked when a sale is delivered. */
    DELIVERY,
    /** Opening capital or opening stock. */
    OPENING,
    /** An operating expense, or a hand-written correction. */
    MANUAL
}
