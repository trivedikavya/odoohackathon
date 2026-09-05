package com.urbanfurniture.accounting.master.account;

/**
 * Accounts the posting engine resolves by a stable code rather than by name,
 * so renaming an account in the UI can never break the double-entry mapping.
 */
public enum SystemAccount {
    CASH,
    BANK,
    DEBTORS,
    CREDITORS,
    TAX_RECEIVABLE,
    TAX_PAYABLE,
    INVENTORY,
    CAPITAL,
    SALES_INCOME,
    PURCHASE_EXPENSE
}
