package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.DocumentNumberService;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The one and only writer of ledger entries.
 * <p>
 * No controller, and no other service, may construct a {@link JournalEntry}
 * directly. Everything funnels through {@link #post(JournalEntryDraft)}, which
 * refuses to persist anything that is not a valid balanced double entry:
 * <ol>
 *   <li>at least two lines,</li>
 *   <li>every line strictly one-sided and positive,</li>
 *   <li><b>SUM(debit) == SUM(credit)</b>.</li>
 * </ol>
 * Amounts are never taken from the client; callers compute them from persisted
 * document totals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalPostingService {

    private final JournalEntryRepository journalEntryRepository;
    private final DocumentNumberService documentNumberService;

    /**
     * Validates and persists a draft entry.
     * <p>
     * Runs inside the caller's transaction ({@code MANDATORY}) so an invalid
     * entry rolls back the originating invoice/bill/payment with it - the
     * ledger and the documents can never disagree.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(JournalEntryDraft draft) {
        validate(draft);

        JournalEntry entry = JournalEntry.builder()
                .entryNo(documentNumberService.next(DocumentType.JOURNAL_ENTRY))
                .journal(draft.getJournal())
                .entryDate(draft.getEntryDate())
                .sourceType(draft.getSourceType())
                .sourceId(draft.getSourceId())
                .narration(draft.getNarration())
                .build();

        for (JournalEntryDraft.DraftLine line : draft.getLines()) {
            entry.addLine(JournalLine.builder()
                    .account(line.account())
                    .contact(line.contact())
                    .debit(line.debit())
                    .credit(line.credit())
                    .label(line.label())
                    .build());
        }

        // Re-check the invariant on the assembled entity, not just the draft.
        assertBalanced(entry.totalDebit(), entry.totalCredit(), entry.getNarration());

        JournalEntry saved = journalEntryRepository.save(entry);
        log.info("Posted {} {} | {} | debit={} credit={}",
                saved.getSourceType(), saved.getEntryNo(), saved.getNarration(),
                saved.totalDebit(), saved.totalCredit());
        return saved;
    }

    private void validate(JournalEntryDraft draft) {
        if (draft.getJournal() == null) {
            throw new ApiExceptions.UnbalancedEntryException("Journal entry has no journal");
        }
        if (draft.getEntryDate() == null) {
            throw new ApiExceptions.UnbalancedEntryException("Journal entry has no date");
        }
        if (draft.getLines().size() < 2) {
            throw new ApiExceptions.UnbalancedEntryException(
                    "A double-entry needs at least two lines, got " + draft.getLines().size()
                            + " (" + draft.getNarration() + ")");
        }
        for (JournalEntryDraft.DraftLine line : draft.getLines()) {
            if (line.account() == null) {
                throw new ApiExceptions.UnbalancedEntryException("Journal line has no account");
            }
            boolean isDebit = Money.isPositive(line.debit());
            boolean isCredit = Money.isPositive(line.credit());
            if (isDebit == isCredit) {
                throw new ApiExceptions.UnbalancedEntryException(
                        "Journal line on account " + line.account().getCode()
                                + " must be exactly one of debit or credit (debit=" + line.debit()
                                + ", credit=" + line.credit() + ")");
            }
            if (Money.isNegative(line.debit()) || Money.isNegative(line.credit())) {
                throw new ApiExceptions.UnbalancedEntryException(
                        "Journal line amounts must be positive on account " + line.account().getCode());
            }
        }
        assertBalanced(draft.totalDebit(), draft.totalCredit(), draft.getNarration());
    }

    /** The rule the whole system rests on. */
    private void assertBalanced(BigDecimal totalDebit, BigDecimal totalCredit, String context) {
        if (!Money.eq(totalDebit, totalCredit)) {
            throw new ApiExceptions.UnbalancedEntryException(
                    "Unbalanced journal entry rejected: debit=" + Money.of(totalDebit)
                            + " credit=" + Money.of(totalCredit)
                            + " difference=" + Money.subtract(totalDebit, totalCredit)
                            + (context == null ? "" : " (" + context + ")"));
        }
    }
}
