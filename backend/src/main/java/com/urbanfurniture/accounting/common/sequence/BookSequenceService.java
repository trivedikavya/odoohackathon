package com.urbanfurniture.accounting.common.sequence;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates the next document number for a book.
 * <p>
 * The row is taken under {@code SELECT ... FOR UPDATE} inside the
 * caller's transaction, so two concurrent invoices in the same book
 * cannot receive the same number, and an abandoned transaction rolls the
 * counter back instead of leaving a gap.
 */
@Service
@RequiredArgsConstructor
public class BookSequenceService {

    private final DocSequenceRepository docSequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Book book, DocumentType type) {
        DocSequence sequence = docSequenceRepository
                .lockByBookAndType(book.getId(), type.name())
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Document sequence not configured for " + type + " in book " + book.getId()));

        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        docSequenceRepository.save(sequence);

        return String.format("%s-%0" + sequence.getPadding() + "d", sequence.getPrefix(), value);
    }
}
