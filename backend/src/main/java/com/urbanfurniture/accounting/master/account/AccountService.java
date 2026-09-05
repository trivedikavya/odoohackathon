package com.urbanfurniture.accounting.master.account;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Chart of Accounts configuration. Reads are open to staff; every mutation is
 * owner-only and refuses to touch system accounts, which the posting engine
 * depends on.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountResponse> list(boolean includeArchived) {
        List<Account> accounts = includeArchived
                ? accountRepository.findAllByOrderByCodeAsc()
                : accountRepository.findByActiveTrueOrderByCodeAsc();
        return accounts.stream().map(AccountDtos.AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountDtos.AccountResponse get(Long id) {
        return AccountDtos.AccountResponse.from(require(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountDtos.AccountResponse create(AccountDtos.AccountRequest request) {
        if (accountRepository.existsByCodeIgnoreCase(request.code().trim())) {
            throw new ApiExceptions.ConflictException("An account with code " + request.code() + " already exists");
        }
        Account account = Account.builder()
                .code(request.code().trim())
                .name(request.name().trim())
                .type(request.type())
                .isSystem(false)
                .active(true)
                .build();
        return AccountDtos.AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountDtos.AccountResponse update(Long id, AccountDtos.AccountRequest request) {
        Account account = require(id);

        if (Boolean.TRUE.equals(account.getIsSystem()) && account.getType() != request.type()) {
            throw new ApiExceptions.BusinessRuleException(
                    "The type of a system account cannot be changed - the posting rules depend on it");
        }
        if (!account.getCode().equalsIgnoreCase(request.code().trim())
                && accountRepository.existsByCodeIgnoreCase(request.code().trim())) {
            throw new ApiExceptions.ConflictException("An account with code " + request.code() + " already exists");
        }

        account.setCode(request.code().trim());
        account.setName(request.name().trim());
        account.setType(request.type());
        return AccountDtos.AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountDtos.AccountResponse setArchived(Long id, boolean archived) {
        Account account = require(id);
        if (archived && Boolean.TRUE.equals(account.getIsSystem())) {
            throw new ApiExceptions.BusinessRuleException(
                    "System accounts cannot be archived - they are required by the double-entry mapping");
        }
        account.setActive(!archived);
        return AccountDtos.AccountResponse.from(accountRepository.save(account));
    }

    private Account require(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Account", id));
    }
}
