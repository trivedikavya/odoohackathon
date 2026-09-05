package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.Money;

import java.math.BigDecimal;

/**
 * Aggregated debit and credit totals for one account, straight out of the
 * ledger. Every balance figure in every report is derived from this.
 */
public record AccountBalanceRow(
        Long accountId,
        String code,
        String name,
        AccountType type,
        /** Lets a caller pick a well-known account out of a full listing
         *  instead of issuing a second query for it. Null for user-created accounts. */
        SystemAccount systemCode,
        BigDecimal totalDebit,
        BigDecimal totalCredit) {

    public AccountBalanceRow {
        totalDebit = Money.of(totalDebit);
        totalCredit = Money.of(totalCredit);
    }

    /**
     * Balance on the account's natural side, so every report figure reads
     * as a positive number under normal conditions.
     */
    public BigDecimal naturalBalance() {
        return type.isDebitNormal()
                ? Money.subtract(totalDebit, totalCredit)
                : Money.subtract(totalCredit, totalDebit);
    }

    /** Debit-positive signed balance; used to prove the ledger sums to zero. */
    public BigDecimal signedDebitBalance() {
        return Money.subtract(totalDebit, totalCredit);
    }
}
