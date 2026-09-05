package com.urbanfurniture.accounting.transaction.payment;

import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.transaction.TransactionDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "List payments (scoped to the caller's own contact for portal users)")
    public PageResponse<TransactionDtos.PaymentResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PaymentDirection direction,
            @PageableDefault(size = 20) Pageable pageable) {
        return paymentService.search(search, direction, pageable);
    }

    @GetMapping("/{id}")
    public TransactionDtos.PaymentResponse get(@PathVariable Long id) {
        return paymentService.get(id);
    }
}
