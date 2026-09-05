package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.analytic.AnalyticAccount;
import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.master.Contact;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent description of a ledger entry, assembled before
 * {@link JournalPostingService} validates and persists it.
 * <p>
 * The draft is bound to a book on creation, and the posting service
 * refuses any line whose account belongs elsewhere. Book isolation is
 * therefore checked at the one place entries are written, rather than
 * relying on every caller to remember.
 * <p>
 * Zero-amount lines are silently dropped: a 0% tax line should simply not
 * exist rather than pollute the ledger, and it would violate the
 * one-sided CHECK constraint anyway.
 */
@Getter
public class JournalEntryDraft {

    private final Book book;
    private final Journal journal;
    private final LocalDate entryDate;
    private final SourceType sourceType;
    private final Long sourceId;
    private final String narration;
    private final List<DraftLine> lines = new ArrayList<>();

    private JournalEntryDraft(Book book, Journal journal, LocalDate entryDate,
                              SourceType sourceType, Long sourceId, String narration) {
        this.book = book;
        this.journal = journal;
        this.entryDate = entryDate;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.narration = narration;
    }

    public static JournalEntryDraft on(Book book, Journal journal, LocalDate entryDate,
                                       SourceType sourceType, Long sourceId, String narration) {
        return new JournalEntryDraft(book, journal, entryDate, sourceType, sourceId, narration);
    }

    public JournalEntryDraft debit(Account account, BigDecimal amount, String label) {
        return debit(account, null, null, amount, label);
    }

    public JournalEntryDraft debit(Account account, Contact contact, BigDecimal amount, String label) {
        return debit(account, contact, null, amount, label);
    }

    public JournalEntryDraft debit(Account account, Contact contact, AnalyticAccount analytic,
                                   BigDecimal amount, String label) {
        if (Money.isPositive(amount)) {
            lines.add(new DraftLine(account, contact, analytic, Money.of(amount), Money.ZERO, label));
        }
        return this;
    }

    public JournalEntryDraft credit(Account account, BigDecimal amount, String label) {
        return credit(account, null, null, amount, label);
    }

    public JournalEntryDraft credit(Account account, Contact contact, BigDecimal amount, String label) {
        return credit(account, contact, null, amount, label);
    }

    public JournalEntryDraft credit(Account account, Contact contact, AnalyticAccount analytic,
                                    BigDecimal amount, String label) {
        if (Money.isPositive(amount)) {
            lines.add(new DraftLine(account, contact, analytic, Money.ZERO, Money.of(amount), label));
        }
        return this;
    }

    public BigDecimal totalDebit() {
        return lines.stream().map(DraftLine::debit).reduce(Money.ZERO, Money::add);
    }

    public BigDecimal totalCredit() {
        return lines.stream().map(DraftLine::credit).reduce(Money.ZERO, Money::add);
    }

    public record DraftLine(Account account, Contact contact, AnalyticAccount analyticAccount,
                            BigDecimal debit, BigDecimal credit, String label) {
    }
}
