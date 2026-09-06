package com.urbanfurniture.accounting.master;

/**
 * How a book relates to a counterparty in its own address book.
 * <p>
 * Purely a label: trading direction is decided per deal, so a party can
 * supply you on Monday and buy from you on Tuesday regardless of what
 * this says. It exists so the contacts list can be filtered sensibly.
 */
public enum ContactRelationship {
    CUSTOMER,
    VENDOR,
    BOTH;

    public boolean canSupply() {
        return this == VENDOR || this == BOTH;
    }

    public boolean canBuy() {
        return this == CUSTOMER || this == BOTH;
    }
}
