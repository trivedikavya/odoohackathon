package com.urbanfurniture.accounting.transaction.capital;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.ledger.JournalEntry;
import com.urbanfurniture.accounting.ledger.JournalEntryDraft;
import com.urbanfurniture.accounting.ledger.JournalPostingService;
import com.urbanfurniture.accounting.ledger.SourceType;
import com.urbanfurniture.accounting.master.account.AccountLookup;
import com.urbanfurniture.accounting.master.account.SystemAccount;
import com.urbanfurniture.accounting.master.journal.JournalService;
import com.urbanfurniture.accounting.transaction.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records the owner putting money into the business.
 * <p>
 * Without this the Equity side of the balance sheet could only ever hold
 * retained earnings, and the business would appear to trade from an overdrawn
 * bank account. Double-entry mapping:
 * <pre>
 *   Dr  Cash / Bank            amount
 *       Cr  Owner's Capital            amount
 * </pre>
 * Owner-only: an accountant records trading activity, not the capital structure.
 */
@Service
@RequiredArgsConstructor
public class CapitalService {

    private final JournalPostingService journalPostingService;
    private final JournalService journalService;
    private final AccountLookup accounts;

    public record CapitalContributionRequest(
            @NotNull PaymentMethod method,
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 255) String note) {
    }

    public record CapitalContributionResponse(
            Long journalEntryId,
            String journalEntryNo,
            LocalDate date,
            BigDecimal amount,
            PaymentMethod method) {
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CapitalContributionResponse contribute(CapitalContributionRequest request) {
        BigDecimal amount = Money.of(request.amount());
        if (!Money.isPositive(amount)) {
            throw new ApiExceptions.BusinessRuleException("Capital contribution must be greater than zero");
        }

        String narration = request.note() == null || request.note().isBlank()
                ? "Owner capital contribution"
                : request.note().trim();

        JournalEntryDraft draft = JournalEntryDraft.on(
                        journalService.requireByType(request.method().journalType()),
                        request.date(),
                        SourceType.MANUAL,
                        null,
                        narration)
                .debit(accounts.require(request.method().account()), amount,
                        request.method() + " received from owner")
                .credit(accounts.require(SystemAccount.CAPITAL), amount, "Owner's capital");

        JournalEntry entry = journalPostingService.post(draft);

        return new CapitalContributionResponse(
                entry.getId(), entry.getEntryNo(), request.date(), amount, request.method());
    }
}
