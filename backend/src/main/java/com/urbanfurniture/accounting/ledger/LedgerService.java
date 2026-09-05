package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final JournalEntryRepository journalEntryRepository;

    /** Open-ended bounds used when the caller does not supply a date filter. */
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    @Transactional(readOnly = true)
    public PageResponse<LedgerDtos.JournalEntryResponse> search(LocalDate from, LocalDate to,
                                                                SourceType sourceType, Pageable pageable) {
        LocalDate start = from == null ? MIN_DATE : from;
        LocalDate end = to == null ? MAX_DATE : to;
        return PageResponse.of(journalEntryRepository.search(start, end, sourceType, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public LedgerDtos.JournalEntryResponse get(Long id) {
        return toResponse(journalEntryRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Journal entry", id)));
    }

    private LedgerDtos.JournalEntryResponse toResponse(JournalEntry entry) {
        List<LedgerDtos.JournalLineResponse> lines = entry.getLines().stream()
                .map(l -> new LedgerDtos.JournalLineResponse(
                        l.getId(), l.getLineNo(),
                        l.getAccount().getId(), l.getAccount().getCode(), l.getAccount().getName(),
                        l.getContact() == null ? null : l.getContact().getId(),
                        l.getContact() == null ? null : l.getContact().getName(),
                        l.getLabel(), l.getDebit(), l.getCredit()))
                .toList();

        return new LedgerDtos.JournalEntryResponse(
                entry.getId(), entry.getEntryNo(),
                entry.getJournal().getCode(), entry.getJournal().getName(),
                entry.getEntryDate(), entry.getSourceType(), entry.getSourceId(), entry.getNarration(),
                entry.totalDebit(), entry.totalCredit(),
                Money.eq(entry.totalDebit(), entry.totalCredit()),
                entry.getCreatedBy(), lines);
    }
}
