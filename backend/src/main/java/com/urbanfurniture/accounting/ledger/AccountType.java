package com.urbanfurniture.accounting.ledger;

/**
 * The five account classes, and which side each one increases on.
 * <p>
 * {@code debitNormal} is what lets a report show every figure as a
 * positive number: a payable with a credit balance of 10,000 reads as
 * "you owe 10,000", not "-10,000".
 */
public enum AccountType {

    ASSET(true),
    LIABILITY(false),
    EQUITY(false),
    INCOME(false),
    EXPENSE(true);

    private final boolean debitNormal;

    AccountType(boolean debitNormal) {
        this.debitNormal = debitNormal;
    }

    public boolean isDebitNormal() {
        return debitNormal;
    }

    /** Assets, liabilities and equity carry forward; income and expense do not. */
    public boolean isBalanceSheetAccount() {
        return this == ASSET || this == LIABILITY || this == EQUITY;
    }

    public boolean isProfitAndLossAccount() {
        return this == INCOME || this == EXPENSE;
    }
}
