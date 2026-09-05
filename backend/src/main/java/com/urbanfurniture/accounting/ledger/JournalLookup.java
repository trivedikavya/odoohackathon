package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JournalLookup {

    private final JournalRepository journalRepository;

    @Transactional(readOnly = true)
    public Journal require(Book book, JournalType type) {
        return journalRepository.findByBookIdAndType(book.getId(), type)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Book " + book.getId() + " has no " + type + " journal"));
    }
}
