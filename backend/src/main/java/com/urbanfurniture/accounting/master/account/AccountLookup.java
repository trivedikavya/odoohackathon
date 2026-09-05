package com.urbanfurniture.accounting.master.account;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the well-known accounts the posting engine depends on.
 * Fails loudly if the chart of accounts has been tampered with, rather than
 * silently posting to the wrong account.
 */
@Service
@RequiredArgsConstructor
public class AccountLookup {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Account require(SystemAccount systemAccount) {
        return accountRepository.findBySystemCode(systemAccount)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Required system account is missing from the chart of accounts: " + systemAccount));
    }
}
