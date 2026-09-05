package com.urbanfurniture.accounting.ledger;

/** What produced a journal entry. */
public enum SourceType {
    INVOICE,
    BILL,
    PAYMENT,
    OPENING,
    MANUAL
}
