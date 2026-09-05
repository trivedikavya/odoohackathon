package com.urbanfurniture.accounting.identity;

/**
 * What a user may do <em>inside their own party's book</em>.
 * <p>
 * Never grants access to another book. A vendor's ADMIN is an
 * administrator of that vendor's accounts and nothing else.
 */
public enum AccessLevel {

    /** All rights within the book, including users and chart of accounts. */
    ADMIN,

    /**
     * Master data, transactions and reports. Cannot manage users or edit
     * the chart of accounts — those change the shape of the books rather
     * than recording activity in them.
     */
    ACCOUNTANT,

    /**
     * Portal only. Sees their own invoices and bills with paid/unpaid
     * status and can settle them. No masters, no ledger, no reports.
     */
    USER;

    /** Staff roles operate inside a book; USER does not. */
    public boolean isStaff() {
        return this == ADMIN || this == ACCOUNTANT;
    }
}
