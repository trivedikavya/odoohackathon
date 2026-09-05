package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.master.account.Account;
import com.urbanfurniture.accounting.master.contact.Contact;
import com.urbanfurniture.accounting.master.journal.Journal;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent description of a ledger entry, assembled before it is validated and
 * persisted by {@link JournalPostingService}.
 * <p>
 * Zero-amount lines are silently dropped: a 0% tax line, for example, should
 * simply not exist rather than pollute the ledger (and it would violate the
 * one-sided CHECK constraint anyway).
 */
@Getter
public class JournalEntryDraft {

    private final Journal journal;
    private final LocalDate entryDate;
    private final SourceType sourceType;
    private final Long sourceId;
    private final String narration;
    private final List<DraftLine> lines = new ArrayList<>();

    private JournalEntryDraft(Journal journal, LocalDate entryDate, SourceType sourceType,
                              Long sourceId, String narration) {
        this.journal = journal;
        this.entryDate = entryDate;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.narration = narration;
    }

    public static JournalEntryDraft on(Journal journal, LocalDate entryDate, SourceType sourceType,
                                       Long sourceId, String narration) {
        return new JournalEntryDraft(journal, entryDate, sourceType, sourceId, narration);
    }

    public JournalEntryDraft debit(Account account, BigDecimal amount, String label) {
        return debit(account, null, amount, label);
    }

    public JournalEntryDraft debit(Account account, Contact contact, BigDecimal amount, String label) {
        if (Money.isPositive(amount)) {
            lines.add(new DraftLine(account, contact, Money.of(amount), Money.ZERO, label));
        }
        return this;
    }

    public JournalEntryDraft credit(Account account, BigDecimal amount, String label) {
        return credit(account, null, amount, label);
    }

    public JournalEntryDraft credit(Account account, Contact contact, BigDecimal amount, String label) {
        if (Money.isPositive(amount)) {
            lines.add(new DraftLine(account, contact, Money.ZERO, Money.of(amount), label));
        }
        return this;
    }

    public BigDecimal totalDebit() {
        return lines.stream().map(DraftLine::debit).reduce(Money.ZERO, Money::add);
    }

    public BigDecimal totalCredit() {
        return lines.stream().map(DraftLine::credit).reduce(Money.ZERO, Money::add);
    }

    public record DraftLine(Account account, Contact contact, BigDecimal debit, BigDecimal credit, String label) {
    }
}
