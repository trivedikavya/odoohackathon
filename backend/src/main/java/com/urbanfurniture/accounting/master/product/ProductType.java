package com.urbanfurniture.accounting.master.product;

public enum ProductType {
    GOODS,
    SERVICE,
    COMBO;

    /** Only physical goods move stock, so only these appear in the stock report. */
    public boolean isStockTracked() {
        return this == GOODS;
    }
}
