package com.urbanfurniture.accounting.report;

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
@Tag(name = "Financial reports (computed live from the ledger)")
public class ReportController {

    private final FinancialReportService reportService;

    @GetMapping("/balance-sheet")
    @Operation(summary = "Balance Sheet as at a date: Assets = Liabilities + Equity")
    public ReportDtos.BalanceSheet balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportService.balanceSheet(asOf);
    }

    @GetMapping("/profit-and-loss")
    @Operation(summary = "Profit & Loss for a period: Income - Expenses = Net Profit")
    public ReportDtos.ProfitAndLoss profitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.profitAndLoss(from, to);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Headline KPIs for the dashboard")
    public ReportDtos.DashboardSummary dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return reportService.dashboard(asOf);
    }
}
