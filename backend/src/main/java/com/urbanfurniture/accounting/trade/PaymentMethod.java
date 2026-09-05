package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.ledger.JournalType;
import com.urbanfurniture.accounting.ledger.SystemAccount;

public enum PaymentMethod {
    CASH(SystemAccount.CASH, JournalType.CASH),
    BANK(SystemAccount.BANK, JournalType.BANK);

    private final SystemAccount account;
    private final JournalType journal;

    PaymentMethod(SystemAccount account, JournalType journal) {
        this.account = account;
        this.journal = journal;
    }

    public SystemAccount account() {
        return account;
    }

    public JournalType journal() {
        return journal;
    }
}
