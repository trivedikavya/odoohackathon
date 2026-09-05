package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.master.account.SystemAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /**
     * Ledger balances per account up to and including {@code asOf}.
     * Every balance-sheet figure in the system is derived from this query.
     * <p>
     * {@code asOf} is intentionally non-nullable: callers resolve the effective
     * date first, which keeps the bound parameter typed for the driver.
     */
    @Query("""
            select new com.urbanfurniture.accounting.ledger.AccountBalanceRow(
                a.id, a.code, a.name, a.type, sum(l.debit), sum(l.credit))
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where e.entryDate <= :asOf
            group by a.id, a.code, a.name, a.type
            order by a.code
            """)
    List<AccountBalanceRow> balancesAsOf(@Param("asOf") LocalDate asOf);

    /**
     * Ledger balances per account restricted to a date window - used by the
     * Profit &amp; Loss report, which is always period-scoped.
     */
    @Query("""
            select new com.urbanfurniture.accounting.ledger.AccountBalanceRow(
                a.id, a.code, a.name, a.type, sum(l.debit), sum(l.credit))
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where e.entryDate >= :from and e.entryDate <= :to
            group by a.id, a.code, a.name, a.type
            order by a.code
            """)
    List<AccountBalanceRow> balancesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Net balance of a single system account (debit - credit), on its natural side. */
    @Query("""
            select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0)
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where a.systemCode = :systemCode
              and e.entryDate <= :asOf
            """)
    BigDecimal debitBalanceOf(@Param("systemCode") SystemAccount systemCode, @Param("asOf") LocalDate asOf);

    /** Outstanding receivable/payable per contact, derived from the ledger. */
    @Query("""
            select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0)
            from JournalLine l
            join l.account a
            join l.journalEntry e
            where a.systemCode = :systemCode
              and l.contact.id = :contactId
            """)
    BigDecimal debitBalanceOfContact(@Param("systemCode") SystemAccount systemCode,
                                     @Param("contactId") Long contactId);

    List<JournalLine> findByJournalEntryIdOrderByLineNoAsc(Long journalEntryId);
}
