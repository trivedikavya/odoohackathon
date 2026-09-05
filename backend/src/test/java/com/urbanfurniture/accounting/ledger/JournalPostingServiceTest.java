package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.BookSequenceService;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import com.urbanfurniture.accounting.identity.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Guards the two rules the whole system rests on: entries balance, and
 * books never mix.
 */
@ExtendWith(MockitoExtension.class)
class JournalPostingServiceTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private BookSequenceService bookSequenceService;

    @InjectMocks
    private JournalPostingService postingService;

    private Book bookA;
    private Book bookB;
    private Journal salesJournalA;
    private Account cashA;
    private Account salesIncomeA;
    private Account debtorsA;
    private Account cashB;

    @BeforeEach
    void setUp() {
        bookA = Book.builder().id(1L).name("Seller Books").build();
        bookB = Book.builder().id(2L).name("Vendor Books").build();

        salesJournalA = Journal.builder().id(10L).book(bookA).code("SAL")
                .name("Sales Journal").type(JournalType.SALES).active(true).build();

        cashA = account(100L, bookA, "1000", "Cash", AccountType.ASSET);
        debtorsA = account(101L, bookA, "1100", "Accounts Receivable", AccountType.ASSET);
        salesIncomeA = account(102L, bookA, "4000", "Sales Income", AccountType.INCOME);
        cashB = account(200L, bookB, "1000", "Cash", AccountType.ASSET);

        lenient().when(bookSequenceService.next(any(Book.class), eq(DocumentType.JOURNAL_ENTRY)))
                .thenReturn("JE-00001");
        lenient().when(journalEntryRepository.save(any(JournalEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Account account(Long id, Book book, String code, String name, AccountType type) {
        return Account.builder().id(id).book(book).code(code).name(name).type(type).build();
    }

    private JournalEntryDraft draftInA() {
        return JournalEntryDraft.on(bookA, salesJournalA, LocalDate.of(2026, 4, 10),
                SourceType.INVOICE, 1L, "Invoice INV-0001");
    }

    @Test
    @DisplayName("posts a balanced two-sided entry")
    void postsBalancedEntry() {
        JournalEntry entry = postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("11800.00"), "Receivable")
                .credit(salesIncomeA, new BigDecimal("11800.00"), "Sales"));

        assertThat(entry.getEntryNo()).isEqualTo("JE-00001");
        assertThat(entry.getBook().getId()).isEqualTo(1L);
        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.isBalanced()).isTrue();
        verify(journalEntryRepository).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("posts a balanced multi-line entry")
    void postsMultiLineEntry() {
        JournalEntry entry = postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("11800.00"), "Receivable")
                .credit(salesIncomeA, new BigDecimal("10000.00"), "Sales")
                .credit(cashA, new BigDecimal("1800.00"), "Tax"));

        assertThat(entry.getLines()).hasSize(3);
        assertThat(entry.totalDebit()).isEqualByComparingTo("11800.00");
        assertThat(entry.totalCredit()).isEqualByComparingTo("11800.00");
    }

    @Test
    @DisplayName("rejects an unbalanced entry and never reaches the repository")
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("11800.00"), "Receivable")
                .credit(salesIncomeA, new BigDecimal("10000.00"), "Sales")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("does not balance");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects an entry that is out by a single paisa")
    void rejectsOffByOnePaisa() {
        assertThatThrownBy(() -> postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("100.00"), "Receivable")
                .credit(salesIncomeA, new BigDecimal("99.99"), "Sales")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class);

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a single-sided entry")
    void rejectsSingleSidedEntry() {
        assertThatThrownBy(() -> postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("100.00"), "Receivable")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("at least two lines");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("an entry may never reference an account from another book")
    void rejectsCrossBookAccount() {
        // Arithmetically this balances. It is still meaningless: it would
        // credit one company's income against another company's cash.
        assertThatThrownBy(() -> postingService.post(draftInA()
                .debit(cashB, new BigDecimal("100.00"), "Cash in the vendor's book")
                .credit(salesIncomeA, new BigDecimal("100.00"), "Sales in the seller's book")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("belongs to another book");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("an entry may never use another book's journal")
    void rejectsCrossBookJournal() {
        Journal journalB = Journal.builder().id(20L).book(bookB).code("SAL")
                .name("Sales Journal").type(JournalType.SALES).active(true).build();

        assertThatThrownBy(() -> postingService.post(
                JournalEntryDraft.on(bookA, journalB, LocalDate.of(2026, 4, 10),
                                SourceType.INVOICE, 1L, "Cross-book journal")
                        .debit(debtorsA, new BigDecimal("100.00"), "Receivable")
                        .credit(salesIncomeA, new BigDecimal("100.00"), "Sales")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("belongs to another book");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("drops zero-amount lines rather than writing them")
    void dropsZeroAmountLines() {
        // A 0% tax line should not exist, and would violate the one-sided
        // CHECK constraint if it did.
        JournalEntry entry = postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("100.00"), "Receivable")
                .credit(salesIncomeA, new BigDecimal("100.00"), "Sales")
                .credit(cashA, BigDecimal.ZERO, "Zero tax"));

        assertThat(entry.getLines()).hasSize(2);
    }

    @Test
    @DisplayName("numbers lines sequentially from one")
    void numbersLinesSequentially() {
        JournalEntry entry = postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("100.00"), "A")
                .credit(salesIncomeA, new BigDecimal("60.00"), "B")
                .credit(cashA, new BigDecimal("40.00"), "C"));

        assertThat(entry.getLines()).extracting(JournalLine::getLineNo).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("rejects a draft with no book")
    void rejectsMissingBook() {
        assertThatThrownBy(() -> postingService.post(
                JournalEntryDraft.on(null, salesJournalA, LocalDate.now(),
                                SourceType.MANUAL, 1L, "No book")
                        .debit(debtorsA, new BigDecimal("100.00"), "A")
                        .credit(salesIncomeA, new BigDecimal("100.00"), "B")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("no book");
    }

    @Test
    @DisplayName("ignores negative amounts instead of inverting the entry")
    void ignoresNegativeAmounts() {
        // A negative debit is a caller bug. Silently treating it as a
        // credit would post the opposite of what was intended.
        assertThatThrownBy(() -> postingService.post(draftInA()
                .debit(debtorsA, new BigDecimal("-100.00"), "Negative")
                .credit(salesIncomeA, new BigDecimal("100.00"), "Sales")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class);
    }
}
