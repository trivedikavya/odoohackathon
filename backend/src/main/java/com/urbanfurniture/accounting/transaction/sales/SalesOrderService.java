package com.urbanfurniture.accounting.transaction.sales;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.PageResponse;
import com.urbanfurniture.accounting.common.SearchTerms;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.common.sequence.DocumentNumberService;
import com.urbanfurniture.accounting.common.sequence.DocumentType;
import com.urbanfurniture.accounting.master.contact.Contact;
import com.urbanfurniture.accounting.master.contact.ContactService;
import com.urbanfurniture.accounting.master.product.Product;
import com.urbanfurniture.accounting.master.product.ProductService;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.transaction.LineAmounts;
import com.urbanfurniture.accounting.transaction.OrderStatus;
import com.urbanfurniture.accounting.transaction.TransactionDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ContactService contactService;
    private final ProductService productService;
    private final DocumentNumberService documentNumberService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResponse<TransactionDtos.OrderResponse> search(String search, OrderStatus status, Pageable pageable) {
        String term = SearchTerms.normalize(search);
        return PageResponse.of(
                salesOrderRepository.search(term, status, currentUser.contactScopeOrNull(), pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.OrderResponse get(Long id) {
        SalesOrder order = requireOrder(id);
        currentUser.assertCanAccessContact(order.getContact().getId());
        return toResponse(order);
    }

    @Transactional
    public TransactionDtos.OrderResponse create(TransactionDtos.OrderRequest request) {
        Contact customer = contactService.requireContact(request.contactId());
        if (!customer.canBeCustomer()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Contact '" + customer.getName() + "' is not a customer");
        }

        SalesOrder order = SalesOrder.builder()
                .orderNo(documentNumberService.next(DocumentType.SALES_ORDER))
                .contact(customer)
                .orderDate(request.orderDate())
                .status(OrderStatus.DRAFT)
                .notes(request.notes())
                .build();

        applyLines(order, request.lines());
        return toResponse(salesOrderRepository.save(order));
    }

    @Transactional
    public TransactionDtos.OrderResponse update(Long id, TransactionDtos.OrderRequest request) {
        SalesOrder order = requireOrder(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only draft sales orders can be edited (this one is " + order.getStatus() + ")");
        }

        Contact customer = contactService.requireContact(request.contactId());
        if (!customer.canBeCustomer()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Contact '" + customer.getName() + "' is not a customer");
        }

        order.setContact(customer);
        order.setOrderDate(request.orderDate());
        order.setNotes(request.notes());
        order.getLines().clear();
        applyLines(order, request.lines());

        return toResponse(salesOrderRepository.save(order));
    }

    @Transactional
    public TransactionDtos.OrderResponse confirm(Long id) {
        SalesOrder order = requireOrder(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only a draft sales order can be confirmed (this one is " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.CONFIRMED);
        return toResponse(salesOrderRepository.save(order));
    }

    @Transactional
    public TransactionDtos.OrderResponse cancel(Long id) {
        SalesOrder order = requireOrder(id);
        if (order.getStatus() == OrderStatus.INVOICED) {
            throw new ApiExceptions.BusinessRuleException(
                    "This order has already been invoiced; cancel the invoice instead");
        }
        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(salesOrderRepository.save(order));
    }

    // ---------------- internals ----------------

    SalesOrder requireOrder(Long id) {
        return salesOrderRepository.findWithLinesById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Sales order", id));
    }

    private void applyLines(SalesOrder order, List<TransactionDtos.LineRequest> lineRequests) {
        BigDecimal untaxed = Money.ZERO;
        BigDecimal tax = Money.ZERO;
        BigDecimal total = Money.ZERO;

        for (TransactionDtos.LineRequest lr : lineRequests) {
            Product product = productService.requireProduct(lr.productId());
            BigDecimal rate = lr.taxRate() != null ? lr.taxRate() : product.getTaxRate();
            LineAmounts amounts = LineAmounts.compute(lr.quantity(), lr.unitPrice(), rate);

            SalesOrderLine line = SalesOrderLine.builder()
                    .product(product)
                    .quantity(lr.quantity())
                    .unitPrice(Money.of(lr.unitPrice()))
                    .taxRate(rate)
                    .untaxedAmount(amounts.untaxed())
                    .taxAmount(amounts.tax())
                    .lineTotal(amounts.total())
                    .build();
            order.addLine(line);

            untaxed = Money.add(untaxed, amounts.untaxed());
            tax = Money.add(tax, amounts.tax());
            total = Money.add(total, amounts.total());
        }

        order.setUntaxedAmount(untaxed);
        order.setTaxAmount(tax);
        order.setTotalAmount(total);
    }

    TransactionDtos.OrderResponse toResponse(SalesOrder order) {
        Invoice generated = invoiceRepository.findBySalesOrderId(order.getId()).stream()
                .findFirst().orElse(null);

        List<TransactionDtos.LineResponse> lines = order.getLines().stream()
                .map(l -> new TransactionDtos.LineResponse(
                        l.getId(), l.getLineNo(), l.getProduct().getId(), l.getProduct().getName(),
                        l.getQuantity(), l.getUnitPrice(), l.getTaxRate(),
                        l.getUntaxedAmount(), l.getTaxAmount(), l.getLineTotal()))
                .toList();

        return new TransactionDtos.OrderResponse(
                order.getId(), order.getOrderNo(), order.getContact().getId(), order.getContact().getName(),
                order.getOrderDate(), order.getStatus(), order.getUntaxedAmount(), order.getTaxAmount(),
                order.getTotalAmount(), order.getNotes(), lines,
                generated == null ? null : generated.getId(),
                generated == null ? null : generated.getInvoiceNo());
    }
}
