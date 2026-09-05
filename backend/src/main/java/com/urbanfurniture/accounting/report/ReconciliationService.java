package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.identity.BookRepository;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.ledger.JournalLineRepository;
import com.urbanfurniture.accounting.ledger.SystemAccount;
import com.urbanfurniture.accounting.master.Contact;
import com.urbanfurniture.accounting.master.ContactRepository;
import com.urbanfurniture.accounting.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compares each counterparty's position as recorded in our books against
 * the position recorded in theirs.
 * <p>
 * This is the check that proves the mirroring is sound. When we sell to a
 * vendor, our Accounts Receivable balance for them must equal their
 * Accounts Payable balance for us — the two entries came from one deal,
 * so any divergence means a bug, not a business dispute.
 * <p>
 * Counterparties who keep no books are listed but marked not comparable:
 * a customer has no ledger to disagree with.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final ContactRepository contactRepository;
    private final BookRepository bookRepository;
    private final JournalLineRepository journalLineRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public ReportDtos.ReconciliationReport reconcile(LocalDate asOf) {
        Book myBook = currentUser.requireBook();
        Long myPartyId = myBook.getParty().getId();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;

        List<ReportDtos.ReconciliationRow> rows = new ArrayList<>();
        int comparable = 0;
        int matched = 0;

        for (Contact contact : contactRepository.search(myBook.getId(), "", true)) {
            Party other = contact.getParty();

            // Our net position with them, as a single signed figure so a
            // party we both buy from and sell to nets out instead of
            // appearing twice with opposite signs.
            //
            // Both balances are debit-positive: receivable comes back
            // positive when they owe us, payable comes back negative when
            // we owe them. Adding them is therefore already the net
            // position and needs no sign correction.
            BigDecimal ourReceivable = balance(myBook.getId(), SystemAccount.DEBTORS, other.getId(), date);
            BigDecimal ourPayable = balance(myBook.getId(), SystemAccount.CREDITORS, other.getId(), date);
            BigDecimal ourPosition = Money.add(ourReceivable, ourPayable);

            Optional<Book> theirBook = other.keepsBooks()
                    ? bookRepository.findByPartyId(other.getId())
                    : Optional.empty();

            if (theirBook.isEmpty()) {
                rows.add(new ReportDtos.ReconciliationRow(
                        other.getId(), other.getName(), ourPosition, null, null, false, false));
                continue;
            }

            Long theirBookId = theirBook.get().getId();
            BigDecimal theirReceivable = balance(theirBookId, SystemAccount.DEBTORS, myPartyId, date);
            BigDecimal theirPayable = balance(theirBookId, SystemAccount.CREDITORS, myPartyId, date);

            // Their position, expressed from our point of view: what they
            // record as owed to them by us is what we owe them, so the
            // sign flips.
            BigDecimal theirPositionAsOurs = Money.add(theirReceivable, theirPayable).negate();

            BigDecimal difference = Money.subtract(ourPosition, theirPositionAsOurs);
            boolean isMatched = Money.isZero(difference);

            comparable++;
            if (isMatched) {
                matched++;
            }

            rows.add(new ReportDtos.ReconciliationRow(
                    other.getId(), other.getName(), ourPosition, theirPositionAsOurs,
                    difference, isMatched, true));
        }

        return new ReportDtos.ReconciliationReport(
                date, rows, comparable, matched, comparable == matched);
    }

    /**
     * A control account's raw debit-positive balance for one counterparty.
     * <p>
     * Receivable comes back positive (they owe us) and payable comes back
     * negative (we owe them), so adding the two yields a single signed net
     * position without any special-casing.
     */
    private BigDecimal balance(Long bookId, SystemAccount control, Long partyId, LocalDate asOf) {
        return Money.nullSafe(
                journalLineRepository.controlBalanceForParty(bookId, control, partyId, asOf));
    }
}
