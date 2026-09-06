package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.analytic.AnalyticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports, computed live from this book's ledger")
public class ReportController {

    private final FinancialReportService reportService;
    private final AgingReportService agingReportService;
    private final ReconciliationService reconciliationService;
    private final ChartService chartService;
    private final StockReportService stockReportService;
    private final AnalyticService analyticService;

    @GetMapping("/dashboard")
    public ReportDtos.DashboardSummary dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportService.dashboard(asOf);
    }

    @GetMapping("/balance-sheet")
    @Operation(summary = "Assets = Liabilities + Equity, with the difference shown explicitly")
    public ReportDtos.BalanceSheet balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportService.balanceSheet(asOf);
    }

    @GetMapping("/profit-and-loss")
    public ReportDtos.ProfitAndLoss profitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.profitAndLoss(from, to);
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Every account's net position, proving the ledger sums to zero")
    public ReportDtos.TrialBalance trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportService.trialBalance(asOf);
    }

    @GetMapping("/aging")
    @Operation(summary = "AR/AP aging, reconciled against the ledger's control account")
    public ReportDtos.AgingReport aging(
            @RequestParam(required = false) ReportDtos.AgingType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return agingReportService.aging(type, asOf);
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Counterparty reconciliation: what our books say versus what theirs do")
    public ReportDtos.ReconciliationReport reconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reconciliationService.reconcile(asOf);
    }

    @GetMapping("/budget")
    @Operation(summary = "Planned versus actual. Actuals are aggregated live from journal lines.")
    public ReportDtos.BudgetReport budget(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return analyticService.budgetReport(asOf);
    }

    @GetMapping("/stock-ledger")
    @Operation(summary = "Stock on hand, cross-checked against the Inventory account")
    public ReportDtos.StockLedger stockLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return stockReportService.stockLedger(asOf);
    }

    @GetMapping("/sales-trend")
    public ReportDtos.SalesTrend salesTrend(@RequestParam(required = false) Integer months) {
        return chartService.salesTrend(months);
    }
}
