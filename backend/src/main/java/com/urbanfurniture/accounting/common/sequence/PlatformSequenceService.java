package com.urbanfurniture.accounting.common.sequence;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Allocates platform-wide numbers for deals and settlements. */
@Service
@RequiredArgsConstructor
public class PlatformSequenceService {

    public static final String DEAL = "DEAL";
    public static final String SETTLEMENT = "SETTLEMENT";

    private final PlatformSequenceRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String docType) {
        PlatformSequence sequence = repository.lockByType(docType)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Platform sequence not configured for " + docType));

        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        repository.save(sequence);

        return String.format("%s-%0" + sequence.getPadding() + "d", sequence.getPrefix(), value);
    }
}
