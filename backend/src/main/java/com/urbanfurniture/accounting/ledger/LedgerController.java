package com.urbanfurniture.accounting.ledger;

import com.urbanfurniture.accounting.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger (auto-generated journal entries)")
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/journal-entries")
    @Operation(summary = "Browse the ledger. Entries are read-only by design.")
    public PageResponse<LedgerDtos.JournalEntryResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SourceType sourceType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ledgerService.search(from, to, sourceType, pageable);
    }

    @GetMapping("/journal-entries/{id}")
    public LedgerDtos.JournalEntryResponse get(@PathVariable Long id) {
        return ledgerService.get(id);
    }
}
