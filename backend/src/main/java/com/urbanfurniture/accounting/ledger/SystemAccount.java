package com.urbanfurniture.accounting.ledger;

/**
 * Accounts the posting engine resolves by a stable code rather than by
 * name, so renaming "Bank" to "HDFC Current A/c" in the UI can never
 * break the double-entry mapping.
 * <p>
 * Every book gets exactly one account per code, created when the book is
 * provisioned.
 */
public enum SystemAccount {

    CASH,
    BANK,
    /** Accounts Receivable — what customers owe this book. */
    DEBTORS,
    /** Accounts Payable — what this book owes its suppliers. */
    CREDITORS,
    INVENTORY,
    CAPITAL,
    SALES_INCOME,
    PURCHASE_EXPENSE,
    COGS,

    /**
     * Undifferentiated tax, used when the place of supply is unknown and
     * the GST component therefore cannot be determined honestly.
     */
    TAX_RECEIVABLE,
    TAX_PAYABLE,

    // GST components. Central and State GST apply together on an
    // intra-state supply; Integrated GST replaces both across a state
    // border. They are remitted to different authorities and reported on
    // different lines of the return, so they can never share an account.
    CGST_RECEIVABLE,
    SGST_RECEIVABLE,
    IGST_RECEIVABLE,
    CGST_PAYABLE,
    SGST_PAYABLE,
    IGST_PAYABLE
}
