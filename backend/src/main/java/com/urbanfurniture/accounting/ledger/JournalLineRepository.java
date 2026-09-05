package com.urbanfurniture.accounting.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /**
     * Every account's balance in one book up to a date. Every balance
     * sheet, trial balance and dashboard figure comes from this one query.
     */
    @Query("""
            select new com.urbanfurniture.accounting.ledger.AccountBalanceRow(
                a.id, a.code, a.name, a.type, a.systemCode, sum(l.debit), sum(l.credit))
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where e.book.id = :bookId and e.entryDate <= :asOf
            group by a.id, a.code, a.name, a.type, a.systemCode
            order by a.code
            """)
    List<AccountBalanceRow> balancesAsOf(@Param("bookId") Long bookId, @Param("asOf") LocalDate asOf);

    /** Period-scoped balances, for the Profit & Loss report. */
    @Query("""
            select new com.urbanfurniture.accounting.ledger.AccountBalanceRow(
                a.id, a.code, a.name, a.type, a.systemCode, sum(l.debit), sum(l.credit))
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where e.book.id = :bookId and e.entryDate >= :from and e.entryDate <= :to
            group by a.id, a.code, a.name, a.type, a.systemCode
            order by a.code
            """)
    List<AccountBalanceRow> balancesBetween(@Param("bookId") Long bookId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /**
     * A control account's balance restricted to one counterparty — the
     * figure the counterparty reconciliation compares across two books.
     */
    @Query("""
            select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0)
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where e.book.id = :bookId
              and a.systemCode = :systemCode
              and l.contact.party.id = :partyId
              and e.entryDate <= :asOf
            """)
    BigDecimal controlBalanceForParty(@Param("bookId") Long bookId,
                                      @Param("systemCode") SystemAccount systemCode,
                                      @Param("partyId") Long partyId,
                                      @Param("asOf") LocalDate asOf);

    /** Actual spend against one analytic account in a period, expenses only. */
    @Query("""
            select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0)
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where l.analyticAccount.id = :analyticAccountId
              and a.type = com.urbanfurniture.accounting.ledger.AccountType.EXPENSE
              and e.entryDate >= :from and e.entryDate <= :to
            """)
    BigDecimal analyticActualBetween(@Param("analyticAccountId") Long analyticAccountId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** Monthly income and expense totals for the dashboard trend chart. */
    @Query("""
            select cast(function('to_char', e.entryDate, 'YYYY-MM') as string),
                   coalesce(sum(case when a.type = com.urbanfurniture.accounting.ledger.AccountType.INCOME
                                     then l.credit - l.debit else 0 end), 0),
                   coalesce(sum(case when a.type = com.urbanfurniture.accounting.ledger.AccountType.EXPENSE
                                     then l.debit - l.credit else 0 end), 0)
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where e.book.id = :bookId and e.entryDate >= :from and e.entryDate <= :to
            group by function('to_char', e.entryDate, 'YYYY-MM')
            order by function('to_char', e.entryDate, 'YYYY-MM')
            """)
    List<Object[]> monthlyIncomeAndExpense(@Param("bookId") Long bookId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
}
