package com.urbanfurniture.accounting.analytic;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.ledger.JournalLineRepository;
import com.urbanfurniture.accounting.report.ReportDtos;
import com.urbanfurniture.accounting.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AnalyticAccountRepository analyticAccountRepository;
    private final BudgetRepository budgetRepository;
    private final JournalLineRepository journalLineRepository;
    private final CurrentUser currentUser;

    // ---------------- analytic accounts ----------------

    @Transactional(readOnly = true)
    public List<AnalyticDtos.AnalyticAccountResponse> list(boolean includeArchived) {
        return analyticAccountRepository.findForBook(currentUser.requireBookId(), includeArchived)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AnalyticDtos.AnalyticAccountResponse create(AnalyticDtos.AnalyticAccountRequest request) {
        Book book = currentUser.requireBook();
        String code = request.code().trim().toUpperCase();
        if (analyticAccountRepository.existsByBookIdAndCodeIgnoreCase(book.getId(), code)) {
            throw new ApiExceptions.ConflictException("Code '" + code + "' is already in use");
        }
        return toResponse(analyticAccountRepository.save(AnalyticAccount.builder()
                .book(book).code(code).name(request.name().trim())
                .type(request.type()).notes(request.notes()).active(true).build()));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AnalyticDtos.AnalyticAccountResponse update(Long id, AnalyticDtos.AnalyticAccountRequest request) {
        AnalyticAccount account = require(id);
        account.setCode(request.code().trim().toUpperCase());
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setNotes(request.notes());
        return toResponse(analyticAccountRepository.save(account));
    }

    /**
     * Archives rather than deletes: journal lines already tagged keep
     * pointing at it, so historical budget reports stay reproducible.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AnalyticDtos.AnalyticAccountResponse setArchived(Long id, boolean archived) {
        AnalyticAccount account = require(id);
        account.setActive(!archived);
        return toResponse(analyticAccountRepository.save(account));
    }

    // ---------------- budgets ----------------

    @Transactional(readOnly = true)
    public List<AnalyticDtos.BudgetResponse> budgets(boolean includeArchived) {
        return budgetRepository.findForBook(currentUser.requireBookId(), includeArchived)
                .stream().map(this::toBudgetResponse).toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AnalyticDtos.BudgetResponse createBudget(AnalyticDtos.BudgetRequest request) {
        Book book = currentUser.requireBook();
        validatePeriod(request);
        return toBudgetResponse(budgetRepository.save(Budget.builder()
                .book(book)
                .name(request.name().trim())
                .analyticAccount(require(request.analyticAccountId()))
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .plannedAmount(Money.of(request.plannedAmount()))
                .responsible(request.responsible())
                .notes(request.notes())
                .active(true)
                .build()));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AnalyticDtos.BudgetResponse updateBudget(Long id, AnalyticDtos.BudgetRequest request) {
        validatePeriod(request);
        Budget budget = requireBudget(id);
        budget.setName(request.name().trim());
        budget.setAnalyticAccount(require(request.analyticAccountId()));
        budget.setPeriodStart(request.periodStart());
        budget.setPeriodEnd(request.periodEnd());
        budget.setPlannedAmount(Money.of(request.plannedAmount()));
        budget.setResponsible(request.responsible());
        budget.setNotes(request.notes());
        return toBudgetResponse(budgetRepository.save(budget));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AnalyticDtos.BudgetResponse archiveBudget(Long id) {
        Budget budget = requireBudget(id);
        budget.setActive(false);
        return toBudgetResponse(budgetRepository.save(budget));
    }

    /**
     * Planned versus actual.
     * <p>
     * Only the planned figure is read from the budget row. Actuals are
     * aggregated from journal lines, so a draft or cancelled document
     * consumes nothing — only money that actually moved counts.
     */
    @Transactional(readOnly = true)
    public ReportDtos.BudgetReport budgetReport(LocalDate asOf) {
        Long bookId = currentUser.requireBookId();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;

        List<ReportDtos.BudgetPerformance> rows = new ArrayList<>();
        BigDecimal totalPlanned = Money.ZERO;
        BigDecimal totalActual = Money.ZERO;

        for (Budget budget : budgetRepository.findForBook(bookId, false)) {
            // Never count spend beyond the reporting date, even if the
            // budget period runs past it — otherwise a year-long budget
            // reviewed in March would look catastrophically overspent.
            LocalDate windowEnd = budget.getPeriodEnd().isAfter(date) ? date : budget.getPeriodEnd();

            BigDecimal actual = budget.getPeriodStart().isAfter(windowEnd)
                    ? Money.ZERO
                    : Money.nullSafe(journalLineRepository.analyticActualBetween(
                            budget.getAnalyticAccount().getId(), budget.getPeriodStart(), windowEnd));

            BigDecimal planned = Money.nullSafe(budget.getPlannedAmount());
            BigDecimal variance = Money.subtract(planned, actual);
            BigDecimal utilisation = Money.isZero(planned)
                    ? null
                    : actual.multiply(HUNDRED).divide(planned, 1, RoundingMode.HALF_UP);

            rows.add(new ReportDtos.BudgetPerformance(
                    budget.getId(), budget.getName(),
                    budget.getAnalyticAccount().getCode(), budget.getAnalyticAccount().getName(),
                    budget.getPeriodStart(), budget.getPeriodEnd(), budget.getResponsible(),
                    planned, actual, variance, utilisation, Money.isNegative(variance)));

            totalPlanned = Money.add(totalPlanned, planned);
            totalActual = Money.add(totalActual, actual);
        }

        return new ReportDtos.BudgetReport(date, rows, totalPlanned, totalActual,
                Money.subtract(totalPlanned, totalActual));
    }

    // ---------------- internals ----------------

    public AnalyticAccount require(Long id) {
        AnalyticAccount account = analyticAccountRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Analytic account", id));
        currentUser.assertOwnsBook(account.getBook().getId());
        return account;
    }

    private Budget requireBudget(Long id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Budget", id));
        currentUser.assertOwnsBook(budget.getBook().getId());
        return budget;
    }

    private void validatePeriod(AnalyticDtos.BudgetRequest request) {
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new ApiExceptions.BusinessRuleException("Budget period end cannot be before its start");
        }
    }

    private AnalyticDtos.AnalyticAccountResponse toResponse(AnalyticAccount a) {
        return new AnalyticDtos.AnalyticAccountResponse(
                a.getId(), a.getCode(), a.getName(), a.getType(), a.getNotes(), a.getActive());
    }

    private AnalyticDtos.BudgetResponse toBudgetResponse(Budget b) {
        AnalyticAccount a = b.getAnalyticAccount();
        return new AnalyticDtos.BudgetResponse(
                b.getId(), b.getName(), a.getId(), a.getCode(), a.getName(),
                b.getPeriodStart(), b.getPeriodEnd(), b.getPlannedAmount(),
                b.getResponsible(), b.getNotes(), b.getActive());
    }
}
