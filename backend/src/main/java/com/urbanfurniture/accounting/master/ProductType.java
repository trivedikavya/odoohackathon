package com.urbanfurniture.accounting.master;

/**
 * What kind of thing a catalogue item is, and whether it carries stock.
 */
public enum ProductType {

    /** A physical item. Carries stock and a cost of sale. */
    GOODS(true),

    /** Labour or a fee. Nothing to count, expensed when incurred. */
    SERVICE(false),

    /**
     * A bundle sold as one line. Carries no stock of its own - selling
     * one consumes its components, and it is those that are counted and
     * costed. That is what stops a bundle becoming a way to sell stock
     * you do not have.
     */
    COMBO(false);

    private final byte tracksStock;

    ProductType(boolean tracksStock) {
        this.tracksStock = (byte) (tracksStock ? 1 : 0);
    }

    public boolean tracksStock() {
        return tracksStock == 1;
    }

    public boolean isCombo() {
        return this == COMBO;
    }
}
