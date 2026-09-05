package com.urbanfurniture.accounting.common.sequence;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates human-readable document numbers (SO-0001, INV-0007, JE-00012...).
 */
@Service
@RequiredArgsConstructor
public class DocumentNumberService {

    private final DocSequenceRepository repository;

    /**
     * Joins the caller's transaction so the allocated number rolls back with the
     * document if posting fails - no gaps from abandoned attempts.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        DocSequence sequence = repository.findByDocTypeForUpdate(type.name())
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Document sequence not configured for " + type.name()));

        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        repository.save(sequence);

        String pattern = "%s-%0" + sequence.getPadding() + "d";
        return String.format(pattern, sequence.getPrefix(), value);
    }
}
