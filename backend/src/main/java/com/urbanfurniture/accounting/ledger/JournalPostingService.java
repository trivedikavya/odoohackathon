package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.BookSequenceService;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The only class in the system permitted to write to {@code journal_entry}
 * and {@code journal_line}.
 * <p>
 * It guards two invariants, and it is the sole place either is checked:
 * <ol>
 *   <li><b>Entries balance.</b> {@code SUM(debit) = SUM(credit)}, verified
 *       on the assembled entity and not merely on the draft.</li>
 *   <li><b>Books never mix.</b> Every account, and the journal, must belong
 *       to the book the entry is being written into. Without this, a
 *       mirrored deal could accidentally debit the seller's cash and credit
 *       the buyer's payable — an "entry" that balances arithmetically while
 *       being meaningless in either set of books.</li>
 * </ol>
 * Runs with {@code Propagation.MANDATORY} so it always joins the caller's
 * transaction: an invalid entry rolls the originating document back with
 * it, and the ledger and the documents can never disagree.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalPostingService {

    private final JournalEntryRepository journalEntryRepository;
    private final BookSequenceService bookSequenceService;

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(JournalEntryDraft draft) {
        validate(draft);

        JournalEntry entry = JournalEntry.builder()
                .book(draft.getBook())
                .entryNo(bookSequenceService.next(draft.getBook(), DocumentType.JOURNAL_ENTRY))
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
                    .analyticAccount(line.analyticAccount())
                    .debit(line.debit())
                    .credit(line.credit())
                    .label(line.label())
                    .build());
        }

        // Re-check on the assembled entity, not just the draft. The two
        // could only differ through a bug in assembly, which is exactly
        // the case worth catching.
        assertBalanced(entry.totalDebit(), entry.totalCredit(), entry.getNarration());

        JournalEntry saved = journalEntryRepository.save(entry);
        log.info("Posted {} {} in book {} | {} | debit={} credit={}",
                saved.getSourceType(), saved.getEntryNo(), saved.getBook().getId(),
                saved.getNarration(), saved.totalDebit(), saved.totalCredit());
        return saved;
    }

    private void validate(JournalEntryDraft draft) {
        if (draft.getBook() == null || draft.getBook().getId() == null) {
            throw new ApiExceptions.UnbalancedEntryException("Journal entry has no book");
        }
        if (draft.getJournal() == null) {
            throw new ApiExceptions.UnbalancedEntryException("Journal entry has no journal");
        }
        if (draft.getEntryDate() == null) {
            throw new ApiExceptions.UnbalancedEntryException("Journal entry has no date");
        }

        Long bookId = draft.getBook().getId();
        if (!Objects.equals(bookId, draft.getJournal().getBook().getId())) {
            throw new ApiExceptions.UnbalancedEntryException(
                    "Journal '" + draft.getJournal().getCode() + "' belongs to another book");
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
            if (!Objects.equals(bookId, line.account().getBook().getId())) {
                throw new ApiExceptions.UnbalancedEntryException(
                        "Account '" + line.account().getCode() + "' belongs to another book — "
                                + "an entry may never span two sets of books");
            }
            boolean isDebit = Money.isPositive(line.debit());
            boolean isCredit = Money.isPositive(line.credit());
            if (isDebit == isCredit) {
                throw new ApiExceptions.UnbalancedEntryException(
                        "Journal line must be exactly one of debit or credit: " + line.label());
            }
            if (Money.isNegative(line.debit()) || Money.isNegative(line.credit())) {
                throw new ApiExceptions.UnbalancedEntryException(
                        "Journal line amounts cannot be negative: " + line.label());
            }
        }

        assertBalanced(draft.totalDebit(), draft.totalCredit(), draft.getNarration());
    }

    private void assertBalanced(BigDecimal totalDebit, BigDecimal totalCredit, String context) {
        if (!Money.eq(totalDebit, totalCredit)) {
            throw new ApiExceptions.UnbalancedEntryException(
                    "Entry does not balance: debit " + totalDebit + " vs credit " + totalCredit
                            + " (" + context + ")");
        }
    }
}
