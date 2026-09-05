package com.urbanfurniture.accounting.trade;

/** What a document is, from the point of view of the book that holds it. */
public enum DocumentType {
    SALES_ORDER,
    PURCHASE_ORDER,
    INVOICE,
    BILL;

    public boolean isOrder() {
        return this == SALES_ORDER || this == PURCHASE_ORDER;
    }

    /** True for the selling side of a deal. */
    public boolean isSellSide() {
        return this == SALES_ORDER || this == INVOICE;
    }
}
