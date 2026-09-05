package com.urbanfurniture.accounting.common.sequence;

/** Document kinds that carry a per-book running number. */
public enum DocumentType {
    SALES_ORDER("SO", 4),
    PURCHASE_ORDER("PO", 4),
    INVOICE("INV", 4),
    BILL("BILL", 4),
    PAYMENT("PAY", 4),
    JOURNAL_ENTRY("JE", 5);

    private final String defaultPrefix;
    private final int defaultPadding;

    DocumentType(String defaultPrefix, int defaultPadding) {
        this.defaultPrefix = defaultPrefix;
        this.defaultPadding = defaultPadding;
    }

    public String defaultPrefix() {
        return defaultPrefix;
    }

    public int defaultPadding() {
        return defaultPadding;
    }
}
