package com.urbanfurniture.accounting.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record JournalLineResponse(
            Long id,
            Integer lineNo,
            Long accountId,
            String accountCode,
            String accountName,
            Long contactId,
            String contactName,
            String label,
            BigDecimal debit,
            BigDecimal credit) {
    }

    public record JournalEntryResponse(
            Long id,
            String entryNo,
            String journalCode,
            String journalName,
            LocalDate entryDate,
            SourceType sourceType,
            Long sourceId,
            String narration,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            /** Always true for persisted entries - shown to make the invariant visible. */
            boolean balanced,
            String createdBy,
            List<JournalLineResponse> lines) {
    }
}
