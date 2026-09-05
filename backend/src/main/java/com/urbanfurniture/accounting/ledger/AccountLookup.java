package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the well-known accounts the posting engine depends on, within
 * one book.
 * <p>
 * Fails loudly rather than silently posting to the wrong account: a
 * missing system account means the book was provisioned incorrectly, and
 * quietly picking a substitute would corrupt the ledger in a way that is
 * very hard to unpick later.
 */
@Service
@RequiredArgsConstructor
public class AccountLookup {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Account require(Book book, SystemAccount systemAccount) {
        return accountRepository.findByBookIdAndSystemCode(book.getId(), systemAccount)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Book " + book.getId() + " is missing the required system account: " + systemAccount));
    }
}
