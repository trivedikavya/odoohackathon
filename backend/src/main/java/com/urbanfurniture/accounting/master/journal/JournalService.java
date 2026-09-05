package com.urbanfurniture.accounting.master.journal;

import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.master.account.Account;
import com.urbanfurniture.accounting.master.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalRepository journalRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<JournalDtos.JournalResponse> list() {
        return journalRepository.findAllByOrderByIdAsc().stream()
                .map(JournalDtos.JournalResponse::from)
                .toList();
    }

    /**
     * Resolves the journal a posting should land in. Falls back to any active
     * journal of the right type, and fails loudly if the seeded journals have
     * been removed rather than silently posting somewhere arbitrary.
     */
    @Transactional(readOnly = true)
    public Journal requireByType(JournalType type) {
        return journalRepository.findFirstByTypeAndActiveTrueOrderByIdAsc(type)
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "No active " + type + " journal is configured"));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public JournalDtos.JournalResponse create(JournalDtos.JournalRequest request) {
        if (journalRepository.existsByCodeIgnoreCase(request.code().trim())) {
            throw new ApiExceptions.ConflictException("A journal with code " + request.code() + " already exists");
        }
        Journal journal = Journal.builder()
                .code(request.code().trim())
                .name(request.name().trim())
                .type(request.type())
                .defaultAccount(resolveAccount(request.defaultAccountId()))
                .active(true)
                .build();
        return JournalDtos.JournalResponse.from(journalRepository.save(journal));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public JournalDtos.JournalResponse update(Long id, JournalDtos.JournalRequest request) {
        Journal journal = journalRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Journal", id));
        journal.setCode(request.code().trim());
        journal.setName(request.name().trim());
        journal.setType(request.type());
        journal.setDefaultAccount(resolveAccount(request.defaultAccountId()));
        return JournalDtos.JournalResponse.from(journalRepository.save(journal));
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Account", accountId));
    }
}
