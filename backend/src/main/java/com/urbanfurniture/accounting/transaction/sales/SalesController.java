package com.urbanfurniture.accounting.transaction.sales;

import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.transaction.DocumentStatus;
import com.urbanfurniture.accounting.transaction.OrderStatus;
import com.urbanfurniture.accounting.transaction.TransactionDtos;
import com.urbanfurniture.accounting.transaction.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Sales flow (Order -> Invoice -> Payment)")
public class SalesController {

    private final SalesOrderService salesOrderService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    // ---------------- sales orders ----------------

    @GetMapping("/sales-orders")
    public PageResponse<TransactionDtos.OrderResponse> searchOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return salesOrderService.search(search, status, pageable);
    }

    @GetMapping("/sales-orders/{id}")
    public TransactionDtos.OrderResponse getOrder(@PathVariable Long id) {
        return salesOrderService.get(id);
    }

    @PostMapping("/sales-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDtos.OrderResponse createOrder(@Valid @RequestBody TransactionDtos.OrderRequest request) {
        return salesOrderService.create(request);
    }

    @PutMapping("/sales-orders/{id}")
    public TransactionDtos.OrderResponse updateOrder(@PathVariable Long id,
                                                     @Valid @RequestBody TransactionDtos.OrderRequest request) {
        return salesOrderService.update(id, request);
    }

    @PostMapping("/sales-orders/{id}/confirm")
    @Operation(summary = "Confirm a draft sales order")
    public TransactionDtos.OrderResponse confirmOrder(@PathVariable Long id) {
        return salesOrderService.confirm(id);
    }

    @PostMapping("/sales-orders/{id}/cancel")
    public TransactionDtos.OrderResponse cancelOrder(@PathVariable Long id) {
        return salesOrderService.cancel(id);
    }

    @PostMapping("/sales-orders/{id}/create-invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert a confirmed sales order into a draft customer invoice")
    public TransactionDtos.DocumentResponse createInvoiceFromOrder(
            @PathVariable Long id,
            @RequestBody(required = false) TransactionDtos.ConvertRequest request) {
        return invoiceService.createFromSalesOrder(id, request);
    }

    // ---------------- invoices ----------------

    @GetMapping("/invoices")
    public PageResponse<TransactionDtos.DocumentResponse> searchInvoices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DocumentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return invoiceService.search(search, status, pageable);
    }

    @GetMapping("/invoices/{id}")
    public TransactionDtos.DocumentResponse getInvoice(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDtos.DocumentResponse createInvoice(
            @Valid @RequestBody TransactionDtos.DocumentRequest request) {
        return invoiceService.create(request);
    }

    @PostMapping("/invoices/{id}/post")
    @Operation(summary = "Post an invoice to the ledger (generates a balanced journal entry)")
    public TransactionDtos.DocumentResponse postInvoice(@PathVariable Long id) {
        return invoiceService.post(id);
    }

    @PostMapping("/invoices/{id}/cancel")
    public TransactionDtos.DocumentResponse cancelInvoice(@PathVariable Long id) {
        return invoiceService.cancel(id);
    }

    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer payment against an invoice")
    public TransactionDtos.PaymentResponse payInvoice(@PathVariable Long id,
                                                      @Valid @RequestBody TransactionDtos.PaymentRequest request) {
        return paymentService.payInvoice(id, request);
    }
}
