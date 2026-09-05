package com.urbanfurniture.accounting.transaction.payment;

import com.urbanfurniture.accounting.master.account.SystemAccount;
import com.urbanfurniture.accounting.master.journal.JournalType;

public enum PaymentMethod {
    CASH(SystemAccount.CASH, JournalType.CASH),
    BANK(SystemAccount.BANK, JournalType.BANK);

    private final SystemAccount account;
    private final JournalType journalType;

    PaymentMethod(SystemAccount account, JournalType journalType) {
        this.account = account;
        this.journalType = journalType;
    }

    /** The asset account this payment method moves. */
    public SystemAccount account() {
        return account;
    }

    /** The journal the resulting ledger entry is booked in. */
    public JournalType journalType() {
        return journalType;
    }
}
