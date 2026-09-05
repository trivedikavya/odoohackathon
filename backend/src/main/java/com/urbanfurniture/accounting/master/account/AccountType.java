package com.urbanfurniture.accounting.master.account;

/**
 * Chart of Accounts classification.
 * <p>
 * Normal balance side per type drives every report:
 * ASSET/EXPENSE increase on the debit side, LIABILITY/EQUITY/INCOME on the
 * credit side.
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

    /** True when a debit increases the balance of this account type. */
    public boolean isDebitNormal() {
        return debitNormal;
    }

    /** Assets and expenses appear on the Balance Sheet / P&L as debit balances. */
    public boolean isBalanceSheetAccount() {
        return this == ASSET || this == LIABILITY || this == EQUITY;
    }

    public boolean isProfitAndLossAccount() {
        return this == INCOME || this == EXPENSE;
    }
}
