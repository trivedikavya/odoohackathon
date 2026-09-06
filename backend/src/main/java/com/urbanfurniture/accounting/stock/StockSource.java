package com.urbanfurniture.accounting.stock;

/** What caused a stock movement. */
public enum StockSource {
    /** Goods received against a vendor bill. */
    BILL,
    /** Goods issued when a sale was delivered. */
    DELIVERY,
    /** Stock the book started with. */
    OPENING,
    /** A manual correction - breakage, a stock count difference. */
    ADJUSTMENT
}
