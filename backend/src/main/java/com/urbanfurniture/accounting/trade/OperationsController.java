package com.urbanfurniture.accounting.trade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
@Tag(name = "Capital, opening stock, expenses and over-the-counter trades")
public class OperationsController {

    private final OperationsService operationsService;

    @PostMapping("/capital")
    @Operation(summary = "Owner puts money into the business")
    public Map<String, Object> capital(@Valid @RequestBody OperationsDtos.CapitalRequest request) {
        var entry = operationsService.contributeCapital(request);
        return Map.of("journalEntryId", entry.getId(), "entryNo", entry.getEntryNo());
    }

    @PostMapping("/opening-stock")
    @Operation(summary = "Stock the book already owned when it started")
    public Map<String, Object> openingStock(
            @Valid @RequestBody OperationsDtos.OpeningStockRequest request) {
        var entry = operationsService.openingStock(request);
        return Map.of("journalEntryId", entry.getId(), "entryNo", entry.getEntryNo());
    }

    @PostMapping("/expenses")
    @Operation(summary = "Record an overhead paid from cash or bank")
    public Map<String, Object> expense(@Valid @RequestBody OperationsDtos.ExpenseRequest request) {
        var entry = operationsService.recordExpense(request);
        return Map.of("journalEntryId", entry.getId(), "entryNo", entry.getEntryNo());
    }

    @PostMapping("/direct-purchase")
    @Operation(summary = "Record a completed purchase from an offline supplier")
    public TradeDtos.DealResponse directPurchase(
            @Valid @RequestBody OperationsDtos.DirectTradeRequest request) {
        return operationsService.directPurchase(request);
    }

    @PostMapping("/direct-sale")
    @Operation(summary = "Record a counter sale to a walk-in customer")
    public TradeDtos.DealResponse directSale(
            @Valid @RequestBody OperationsDtos.DirectTradeRequest request) {
        return operationsService.directSale(request);
    }
}
