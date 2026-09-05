package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read access to this book's ledger. */
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
@Tag(name = "General ledger")
public class LedgerController {

    /** Sentinels so the date parameters are always bound with a concrete type. */
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);

    private final JournalEntryRepository journalEntryRepository;
    private final CurrentUser currentUser;

    public record JournalLineResponse(
            Long id, Integer lineNo, Long accountId, String accountCode, String accountName,
            Long contactId, String contactName, String analyticAccountCode,
            String label, BigDecimal debit, BigDecimal credit) {
    }

    public record JournalEntryResponse(
            Long id, String entryNo, String journalCode, String journalName,
            LocalDate entryDate, SourceType sourceType, Long sourceId, String narration,
            BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced,
            String createdBy, List<JournalLineResponse> lines) {
    }

    @GetMapping("/journal-entries")
    @Operation(summary = "This book's journal entries, newest first")
    @Transactional(readOnly = true)
    public PageResponse<JournalEntryResponse> search(
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25) Pageable pageable) {

        return PageResponse.of(
                journalEntryRepository.search(
                        currentUser.requireBookId(), sourceType,
                        from == null ? MIN_DATE : from,
                        to == null ? MAX_DATE : to,
                        pageable),
                this::toResponse);
    }

    @GetMapping("/journal-entries/{id}")
    @Transactional(readOnly = true)
    public JournalEntryResponse get(@PathVariable Long id) {
        JournalEntry entry = journalEntryRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Journal entry", id));
        currentUser.assertOwnsBook(entry.getBook().getId());
        return toResponse(entry);
    }

    private JournalEntryResponse toResponse(JournalEntry e) {
        List<JournalLineResponse> lines = e.getLines().stream()
                .map(l -> new JournalLineResponse(
                        l.getId(), l.getLineNo(),
                        l.getAccount().getId(), l.getAccount().getCode(), l.getAccount().getName(),
                        l.getContact() == null ? null : l.getContact().getParty().getId(),
                        l.getContact() == null ? null : l.getContact().getParty().getName(),
                        l.getAnalyticAccount() == null ? null : l.getAnalyticAccount().getCode(),
                        l.getLabel(), l.getDebit(), l.getCredit()))
                .toList();

        return new JournalEntryResponse(
                e.getId(), e.getEntryNo(), e.getJournal().getCode(), e.getJournal().getName(),
                e.getEntryDate(), e.getSourceType(), e.getSourceId(), e.getNarration(),
                e.totalDebit(), e.totalCredit(), e.isBalanced(), e.getCreatedBy(), lines);
    }
}
