package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.DocumentNumberService;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import com.urbanfurniture.accounting.master.account.Account;
import com.urbanfurniture.accounting.master.account.AccountType;
import com.urbanfurniture.accounting.master.journal.Journal;
import com.urbanfurniture.accounting.master.journal.JournalType;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the single rule the whole system rests on: no journal entry may be
 * persisted unless SUM(debit) == SUM(credit).
 */
@ExtendWith(MockitoExtension.class)
class JournalPostingServiceTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private DocumentNumberService documentNumberService;

    @InjectMocks
    private JournalPostingService postingService;

    private Journal salesJournal;
    private Account debtors;
    private Account salesIncome;
    private Account taxPayable;

    @BeforeEach
    void setUp() {
        salesJournal = Journal.builder().id(1L).code("SAL").name("Sales Journal")
                .type(JournalType.SALES).active(true).build();
        debtors = Account.builder().id(10L).code("1100").name("Debtors").type(AccountType.ASSET).build();
        salesIncome = Account.builder().id(11L).code("4000").name("Sales Income").type(AccountType.INCOME).build();
        taxPayable = Account.builder().id(12L).code("2100").name("Tax Payable").type(AccountType.LIABILITY).build();

        lenient().when(documentNumberService.next(DocumentType.JOURNAL_ENTRY)).thenReturn("JE-00001");
        lenient().when(journalEntryRepository.save(any(JournalEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private JournalEntryDraft draft() {
        return JournalEntryDraft.on(salesJournal, LocalDate.of(2026, 8, 5),
                SourceType.INVOICE, 1L, "Customer invoice INV-0001");
    }

    @Test
    @DisplayName("posts a balanced two-sided entry")
    void postsBalancedEntry() {
        JournalEntry entry = postingService.post(draft()
                .debit(debtors, new BigDecimal("75000.00"), "Receivable")
                .credit(salesIncome, new BigDecimal("75000.00"), "Sales"));

        assertThat(entry.getEntryNo()).isEqualTo("JE-00001");
        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.totalDebit()).isEqualByComparingTo("75000.00");
        assertThat(entry.totalCredit()).isEqualByComparingTo("75000.00");
        verify(journalEntryRepository).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("posts a balanced multi-line entry (invoice split across income and tax)")
    void postsBalancedMultiLineEntry() {
        JournalEntry entry = postingService.post(draft()
                .debit(debtors, new BigDecimal("11800.00"), "Receivable")
                .credit(salesIncome, new BigDecimal("10000.00"), "Sales")
                .credit(taxPayable, new BigDecimal("1800.00"), "Output tax"));

        assertThat(entry.getLines()).hasSize(3);
        assertThat(entry.totalDebit()).isEqualByComparingTo(entry.totalCredit());
        assertThat(entry.totalDebit()).isEqualByComparingTo("11800.00");
    }

    @Test
    @DisplayName("rejects an unbalanced entry and never touches the repository")
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> postingService.post(draft()
                .debit(debtors, new BigDecimal("75000.00"), "Receivable")
                .credit(salesIncome, new BigDecimal("70000.00"), "Sales")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("Unbalanced journal entry rejected")
                .hasMessageContaining("difference=5000.00");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects an entry that is off by a single paisa")
    void rejectsOffByOnePaisa() {
        assertThatThrownBy(() -> postingService.post(draft()
                .debit(debtors, new BigDecimal("100.00"), "Receivable")
                .credit(salesIncome, new BigDecimal("99.99"), "Sales")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class);

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a single-sided entry")
    void rejectsSingleLineEntry() {
        assertThatThrownBy(() -> postingService.post(draft()
                .debit(debtors, new BigDecimal("75000.00"), "Receivable")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("at least two lines");

        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("drops zero-amount lines rather than writing them to the ledger")
    void dropsZeroAmountLines() {
        // A 0% tax line must simply not exist - it would violate the
        // one-sided CHECK constraint on journal_line.
        JournalEntry entry = postingService.post(draft()
                .debit(debtors, new BigDecimal("10000.00"), "Receivable")
                .credit(salesIncome, new BigDecimal("10000.00"), "Sales")
                .credit(taxPayable, BigDecimal.ZERO, "Output tax"));

        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.getLines()).noneMatch(l ->
                l.getDebit().signum() == 0 && l.getCredit().signum() == 0);
    }

    @Test
    @DisplayName("numbers lines sequentially from one")
    void numbersLinesSequentially() {
        JournalEntry entry = postingService.post(draft()
                .debit(debtors, new BigDecimal("11800.00"), "Receivable")
                .credit(salesIncome, new BigDecimal("10000.00"), "Sales")
                .credit(taxPayable, new BigDecimal("1800.00"), "Output tax"));

        assertThat(entry.getLines()).extracting(JournalLine::getLineNo).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("rejects an entry with no journal")
    void rejectsEntryWithoutJournal() {
        JournalEntryDraft noJournal = JournalEntryDraft.on(null, LocalDate.now(),
                        SourceType.MANUAL, null, "broken")
                .debit(debtors, new BigDecimal("10.00"), "a")
                .credit(salesIncome, new BigDecimal("10.00"), "b");

        assertThatThrownBy(() -> postingService.post(noJournal))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class)
                .hasMessageContaining("no journal");
    }

    @Test
    @DisplayName("negative amounts never become ledger lines")
    void ignoresNegativeAmounts() {
        // Money.isPositive() filters them out at draft time, so the entry ends
        // up single-sided and is rejected rather than silently mis-posting.
        assertThatThrownBy(() -> postingService.post(draft()
                .debit(debtors, new BigDecimal("-500.00"), "Negative")
                .credit(salesIncome, new BigDecimal("500.00"), "Sales")))
                .isInstanceOf(ApiExceptions.UnbalancedEntryException.class);

        verify(journalEntryRepository, never()).save(any());
    }
}
