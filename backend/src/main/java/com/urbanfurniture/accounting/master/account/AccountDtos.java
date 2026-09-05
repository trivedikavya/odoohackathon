package com.urbanfurniture.accounting.master.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull AccountType type) {
    }

    public record AccountResponse(
            Long id,
            String code,
            String name,
            AccountType type,
            SystemAccount systemCode,
            Boolean isSystem,
            Boolean active) {

        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getCode(), a.getName(), a.getType(),
                    a.getSystemCode(), a.getIsSystem(), a.getActive());
        }
    }
}
