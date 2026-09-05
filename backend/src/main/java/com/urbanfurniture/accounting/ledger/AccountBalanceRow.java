package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.master.account.AccountType;

import java.math.BigDecimal;

/**
 * Aggregated debit/credit totals for one account, straight out of the ledger.
 * This is the only source used by the financial reports.
 */
public record AccountBalanceRow(
        Long accountId,
        String code,
        String name,
        AccountType type,
        BigDecimal totalDebit,
        BigDecimal totalCredit) {

    public AccountBalanceRow {
        totalDebit = Money.of(totalDebit);
        totalCredit = Money.of(totalCredit);
    }

    /**
     * Balance expressed on the account's natural side, so every figure a report
     * shows is a positive number under normal conditions.
     */
    public BigDecimal naturalBalance() {
        return type.isDebitNormal()
                ? Money.subtract(totalDebit, totalCredit)
                : Money.subtract(totalCredit, totalDebit);
    }

    /** Signed balance in debit-positive terms; used to prove the ledger sums to zero. */
    public BigDecimal signedDebitBalance() {
        return Money.subtract(totalDebit, totalCredit);
    }
}
