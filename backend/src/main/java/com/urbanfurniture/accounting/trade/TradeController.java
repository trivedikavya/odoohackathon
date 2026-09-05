package com.urbanfurniture.accounting.trade;

import com.urbanfurniture.accounting.common.PageResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Trade: quotations, orders, invoices and settlements")
public class TradeController {

    private final DealService dealService;
    private final DocumentService documentService;
    private final SettlementService settlementService;

    // ---------------- deals ----------------

    @GetMapping("/deals")
    @Operation(summary = "Deals this party is on either side of")
    public PageResponse<TradeDtos.DealResponse> searchDeals(
            @RequestParam(required = false) DealStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25) Pageable pageable) {
        return dealService.search(status, search, pageable);
    }

    @GetMapping("/deals/inbox")
    @Operation(summary = "Requests awaiting your decision")
    public PageResponse<TradeDtos.DealResponse> inbox(@PageableDefault(size = 25) Pageable pageable) {
        return dealService.inbox(pageable);
    }

    @GetMapping("/deals/{id}")
    public TradeDtos.DealResponse getDeal(@PathVariable Long id) {
        return dealService.get(id);
    }

    @PostMapping("/deals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a request for quotation. You are the buyer.")
    public TradeDtos.DealResponse createDeal(@Valid @RequestBody TradeDtos.CreateDealRequest request) {
        return dealService.create(request);
    }

    @PostMapping("/deals/{id}/send")
    @Operation(summary = "Send the request to the supplier")
    public TradeDtos.DealResponse send(@PathVariable Long id) {
        return dealService.send(id);
    }

    @PostMapping("/deals/{id}/accept")
    @Operation(summary = "Accept a request. Only the counterparty may accept.")
    public TradeDtos.DealResponse accept(@PathVariable Long id) {
        return dealService.accept(id);
    }

    @PostMapping("/deals/{id}/reject")
    @Operation(summary = "Decline a request")
    public TradeDtos.DealResponse reject(@PathVariable Long id,
                                         @RequestBody(required = false) TradeDtos.RejectRequest request) {
        return dealService.reject(id, request);
    }

    @PostMapping("/deals/{id}/deliver")
    @Operation(summary = "Mark the goods delivered. Supplying party only.")
    public TradeDtos.DealResponse deliver(@PathVariable Long id,
                                          @RequestBody(required = false) TradeDtos.DeliverRequest request) {
        return dealService.markDelivered(id, request);
    }

    @PostMapping("/deals/{id}/invoice")
    @Operation(summary = "Issue and post the invoice. Mirrors a bill into the buyer's books.")
    public TradeDtos.DealResponse invoice(@PathVariable Long id,
                                          @RequestBody(required = false) TradeDtos.InvoiceRequest request) {
        return documentService.invoice(id, request);
    }

    @PostMapping("/deals/{id}/settle")
    @Operation(summary = "Record a payment against this deal, mirrored into both books")
    public TradeDtos.DealResponse settle(@PathVariable Long id,
                                         @Valid @RequestBody TradeDtos.SettlementRequest request) {
        return settlementService.settle(id, request);
    }

    @PostMapping("/deals/{id}/cancel")
    public TradeDtos.DealResponse cancelDeal(@PathVariable Long id) {
        return dealService.cancel(id);
    }

    // ---------------- documents ----------------

    @GetMapping("/documents")
    @Operation(summary = "This book's paperwork of one kind")
    public PageResponse<TradeDtos.DocumentResponse> searchDocuments(
            @RequestParam DocumentType docType,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25) Pageable pageable) {
        return documentService.search(docType, status, search, pageable);
    }

    @GetMapping("/documents/{id}")
    public TradeDtos.DocumentResponse getDocument(@PathVariable Long id) {
        return documentService.get(id);
    }

    @PostMapping("/documents/{id}/tag-line")
    @Operation(summary = "Attach a project to a line in your own book")
    public TradeDtos.DocumentResponse tagLine(@PathVariable Long id,
                                              @Valid @RequestBody TradeDtos.TagLineRequest request) {
        return documentService.tagLine(id, request);
    }

    // ---------------- payments ----------------

    @GetMapping("/payments")
    public PageResponse<TradeDtos.PaymentResponse> searchPayments(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25) Pageable pageable) {
        return settlementService.search(search, pageable);
    }
}
