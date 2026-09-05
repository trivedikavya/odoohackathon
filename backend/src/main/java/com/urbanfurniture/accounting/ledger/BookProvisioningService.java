package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.sequence.DocSequence;
import com.urbanfurniture.accounting.common.sequence.DocSequenceRepository;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import com.urbanfurniture.accounting.identity.Book;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits out a newly created book with everything the posting engine needs.
 * <p>
 * This is the <em>single</em> definition of what a new book contains.
 * Signup and the demo seeder both call it, so a book created by a user
 * registering and a book created by a script are identical — there is no
 * second, drifting copy of the chart of accounts in a SQL seed file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookProvisioningService {

    private final AccountRepository accountRepository;
    private final JournalRepository journalRepository;
    private final DocSequenceRepository docSequenceRepository;

    /** One row of the standard chart of accounts. */
    private record AccountTemplate(String code, String name, AccountType type, SystemAccount systemCode) {
    }

    private static final List<AccountTemplate> CHART_OF_ACCOUNTS = List.of(
            new AccountTemplate("1000", "Cash", AccountType.ASSET, SystemAccount.CASH),
            new AccountTemplate("1010", "Bank", AccountType.ASSET, SystemAccount.BANK),
            new AccountTemplate("1100", "Accounts Receivable", AccountType.ASSET, SystemAccount.DEBTORS),
            new AccountTemplate("1200", "Input GST", AccountType.ASSET, SystemAccount.TAX_RECEIVABLE),
            new AccountTemplate("1210", "CGST Input Credit", AccountType.ASSET, SystemAccount.CGST_RECEIVABLE),
            new AccountTemplate("1220", "SGST Input Credit", AccountType.ASSET, SystemAccount.SGST_RECEIVABLE),
            new AccountTemplate("1230", "IGST Input Credit", AccountType.ASSET, SystemAccount.IGST_RECEIVABLE),
            new AccountTemplate("1300", "Inventory", AccountType.ASSET, SystemAccount.INVENTORY),
            new AccountTemplate("2000", "Accounts Payable", AccountType.LIABILITY, SystemAccount.CREDITORS),
            new AccountTemplate("2100", "Output GST", AccountType.LIABILITY, SystemAccount.TAX_PAYABLE),
            new AccountTemplate("2110", "CGST Payable", AccountType.LIABILITY, SystemAccount.CGST_PAYABLE),
            new AccountTemplate("2120", "SGST Payable", AccountType.LIABILITY, SystemAccount.SGST_PAYABLE),
            new AccountTemplate("2130", "IGST Payable", AccountType.LIABILITY, SystemAccount.IGST_PAYABLE),
            new AccountTemplate("3000", "Owner's Capital", AccountType.EQUITY, SystemAccount.CAPITAL),
            new AccountTemplate("4000", "Sales Income", AccountType.INCOME, SystemAccount.SALES_INCOME),
            new AccountTemplate("4100", "Other Income", AccountType.INCOME, null),
            new AccountTemplate("5000", "Purchase Expense", AccountType.EXPENSE, SystemAccount.PURCHASE_EXPENSE),
            new AccountTemplate("5100", "Cost of Goods Sold", AccountType.EXPENSE, SystemAccount.COGS),
            new AccountTemplate("5200", "Operating Expense", AccountType.EXPENSE, null));

    private record JournalTemplate(String code, String name, JournalType type) {
    }

    private static final List<JournalTemplate> JOURNALS = List.of(
            new JournalTemplate("SAL", "Sales Journal", JournalType.SALES),
            new JournalTemplate("PUR", "Purchase Journal", JournalType.PURCHASE),
            new JournalTemplate("CSH", "Cash Journal", JournalType.CASH),
            new JournalTemplate("BNK", "Bank Journal", JournalType.BANK),
            new JournalTemplate("GEN", "General Journal", JournalType.GENERAL));

    /**
     * Runs inside the caller's transaction so a half-provisioned book can
     * never be left behind: if any part fails, the book itself rolls back
     * with it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void provision(Book book) {
        List<Account> accounts = new ArrayList<>(CHART_OF_ACCOUNTS.size());
        for (AccountTemplate t : CHART_OF_ACCOUNTS) {
            accounts.add(Account.builder()
                    .book(book)
                    .code(t.code())
                    .name(t.name())
                    .type(t.type())
                    .systemCode(t.systemCode())
                    // System accounts are referenced by the posting engine
                    // and must not be archivable.
                    .isSystem(t.systemCode() != null)
                    .active(true)
                    .build());
        }
        accountRepository.saveAll(accounts);

        List<Journal> journals = new ArrayList<>(JOURNALS.size());
        for (JournalTemplate t : JOURNALS) {
            journals.add(Journal.builder()
                    .book(book).code(t.code()).name(t.name()).type(t.type()).active(true).build());
        }
        journalRepository.saveAll(journals);

        List<DocSequence> sequences = new ArrayList<>();
        for (DocumentType type : DocumentType.values()) {
            sequences.add(DocSequence.builder()
                    .book(book)
                    .docType(type.name())
                    .prefix(type.defaultPrefix())
                    .nextValue(1L)
                    .padding(type.defaultPadding())
                    .build());
        }
        docSequenceRepository.saveAll(sequences);

        log.info("Provisioned book {} ({}) with {} accounts, {} journals, {} sequences",
                book.getId(), book.getName(), accounts.size(), journals.size(), sequences.size());
    }
}
