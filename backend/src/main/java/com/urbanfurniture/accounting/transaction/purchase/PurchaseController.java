package com.urbanfurniture.accounting.transaction.purchase;

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
@Tag(name = "Purchase flow (Order -> Bill -> Payment)")
public class PurchaseController {

    private final PurchaseOrderService purchaseOrderService;
    private final BillService billService;
    private final PaymentService paymentService;

    // ---------------- purchase orders ----------------

    @GetMapping("/purchase-orders")
    public PageResponse<TransactionDtos.OrderResponse> searchOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return purchaseOrderService.search(search, status, pageable);
    }

    @GetMapping("/purchase-orders/{id}")
    public TransactionDtos.OrderResponse getOrder(@PathVariable Long id) {
        return purchaseOrderService.get(id);
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDtos.OrderResponse createOrder(@Valid @RequestBody TransactionDtos.OrderRequest request) {
        return purchaseOrderService.create(request);
    }

    @PutMapping("/purchase-orders/{id}")
    public TransactionDtos.OrderResponse updateOrder(@PathVariable Long id,
                                                     @Valid @RequestBody TransactionDtos.OrderRequest request) {
        return purchaseOrderService.update(id, request);
    }

    @PostMapping("/purchase-orders/{id}/confirm")
    @Operation(summary = "Confirm a draft purchase order")
    public TransactionDtos.OrderResponse confirmOrder(@PathVariable Long id) {
        return purchaseOrderService.confirm(id);
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public TransactionDtos.OrderResponse cancelOrder(@PathVariable Long id) {
        return purchaseOrderService.cancel(id);
    }

    @PostMapping("/purchase-orders/{id}/create-bill")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert a confirmed purchase order into a draft vendor bill")
    public TransactionDtos.DocumentResponse createBillFromOrder(
            @PathVariable Long id,
            @RequestBody(required = false) TransactionDtos.ConvertRequest request) {
        return billService.createFromPurchaseOrder(id, request);
    }

    // ---------------- bills ----------------

    @GetMapping("/bills")
    public PageResponse<TransactionDtos.DocumentResponse> searchBills(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DocumentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return billService.search(search, status, pageable);
    }

    @GetMapping("/bills/{id}")
    public TransactionDtos.DocumentResponse getBill(@PathVariable Long id) {
        return billService.get(id);
    }

    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDtos.DocumentResponse createBill(
            @Valid @RequestBody TransactionDtos.DocumentRequest request) {
        return billService.create(request);
    }

    @PostMapping("/bills/{id}/post")
    @Operation(summary = "Post a bill to the ledger (generates a balanced journal entry)")
    public TransactionDtos.DocumentResponse postBill(@PathVariable Long id) {
        return billService.post(id);
    }

    @PostMapping("/bills/{id}/cancel")
    public TransactionDtos.DocumentResponse cancelBill(@PathVariable Long id) {
        return billService.cancel(id);
    }

    @PostMapping("/bills/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a vendor payment against a bill")
    public TransactionDtos.PaymentResponse payBill(@PathVariable Long id,
                                                   @Valid @RequestBody TransactionDtos.PaymentRequest request) {
        return paymentService.payBill(id, request);
    }
}
