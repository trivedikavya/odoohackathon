package com.urbanfurniture.accounting.analytic;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Analytic accounts and budgets")
public class AnalyticController {

    private final AnalyticService service;

    @GetMapping("/analytic-accounts")
    public List<AnalyticDtos.AnalyticAccountResponse> list(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(includeArchived);
    }

    @PostMapping("/analytic-accounts")
    public AnalyticDtos.AnalyticAccountResponse create(
            @Valid @RequestBody AnalyticDtos.AnalyticAccountRequest request) {
        return service.create(request);
    }

    @PutMapping("/analytic-accounts/{id}")
    public AnalyticDtos.AnalyticAccountResponse update(
            @PathVariable Long id, @Valid @RequestBody AnalyticDtos.AnalyticAccountRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/analytic-accounts/{id}/archive")
    public AnalyticDtos.AnalyticAccountResponse archive(@PathVariable Long id) {
        return service.setArchived(id, true);
    }

    @PutMapping("/analytic-accounts/{id}/restore")
    public AnalyticDtos.AnalyticAccountResponse restore(@PathVariable Long id) {
        return service.setArchived(id, false);
    }

    @GetMapping("/budgets")
    public List<AnalyticDtos.BudgetResponse> budgets(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.budgets(includeArchived);
    }

    @PostMapping("/budgets")
    public AnalyticDtos.BudgetResponse createBudget(@Valid @RequestBody AnalyticDtos.BudgetRequest request) {
        return service.createBudget(request);
    }

    @PutMapping("/budgets/{id}")
    public AnalyticDtos.BudgetResponse updateBudget(
            @PathVariable Long id, @Valid @RequestBody AnalyticDtos.BudgetRequest request) {
        return service.updateBudget(id, request);
    }

    @PutMapping("/budgets/{id}/archive")
    public AnalyticDtos.BudgetResponse archiveBudget(@PathVariable Long id) {
        return service.archiveBudget(id);
    }
}
