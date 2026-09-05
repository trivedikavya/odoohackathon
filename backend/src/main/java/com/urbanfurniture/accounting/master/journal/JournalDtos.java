package com.urbanfurniture.accounting.master.journal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class JournalDtos {

    private JournalDtos() {
    }

    public record JournalRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull JournalType type,
            Long defaultAccountId) {
    }

    public record JournalResponse(
            Long id,
            String code,
            String name,
            JournalType type,
            Long defaultAccountId,
            String defaultAccountName,
            Boolean active) {

        public static JournalResponse from(Journal j) {
            return new JournalResponse(
                    j.getId(), j.getCode(), j.getName(), j.getType(),
                    j.getDefaultAccount() == null ? null : j.getDefaultAccount().getId(),
                    j.getDefaultAccount() == null ? null : j.getDefaultAccount().getName(),
                    j.getActive());
        }
    }
}
