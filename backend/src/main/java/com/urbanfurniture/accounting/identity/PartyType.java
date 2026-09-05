package com.urbanfurniture.accounting.identity;

/**
 * What a party <em>is</em> in a trade.
 * <p>
 * Distinct from {@link AccessLevel}, which says what a user may
 * <em>do</em> inside their own party's book. The two are independent: a
 * vendor company has its own admin and its own accountant.
 */
public enum PartyType {

    /** Sells to customers and buys from vendors. Keeps books. */
    SELLER(true),

    /** Supplies sellers, and may also sell direct to customers. Keeps books. */
    VENDOR(true),

    /**
     * Buys from a seller or a vendor. Keeps no books — a customer views
     * and pays invoices, they do not run a chart of accounts.
     */
    CUSTOMER(false);

    private final boolean keepsBooks;

    PartyType(boolean keepsBooks) {
        this.keepsBooks = keepsBooks;
    }

    public boolean keepsBooks() {
        return keepsBooks;
    }

    /** True when this party type is allowed to sell, and so to issue invoices. */
    public boolean canSell() {
        return this == SELLER || this == VENDOR;
    }
}
