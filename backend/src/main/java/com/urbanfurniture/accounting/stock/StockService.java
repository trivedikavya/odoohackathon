package com.urbanfurniture.accounting.stock;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.common.exception.ApiExceptions;
import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.ledger.JournalEntry;
import com.urbanfurniture.accounting.master.Product;
import com.urbanfurniture.accounting.master.ProductComponent;
import com.urbanfurniture.accounting.master.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stock ledger: receipts, issues, and the weighted-average cost that
 * falls out of them.
 * <p>
 * Nothing here is stored as a running total. Every quantity and every
 * average is aggregated from {@link StockMovement} rows at the moment it
 * is needed, for the same reason no report reads a cached balance: a
 * stored figure is a second source of truth, and it goes wrong the first
 * time anything is corrected or back-dated.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMovementRepository movements;
    private final ProductRepository productRepository;

    /** One line's worth of stock to move, after combos have been expanded. */
    public record Consumption(Product product, BigDecimal quantity) {
    }

    // ---------------- reading ----------------

    @Transactional(readOnly = true)
    public StockPosition positionOf(Long bookId, Long productId, LocalDate asOf) {
        List<Object[]> rows = movements.positionAsOf(bookId, productId, asOf);
        if (rows.isEmpty()) {
            return StockPosition.EMPTY;
        }
        Object[] row = rows.get(0);
        if (row == null || row.length < 2 || row[0] == null) {
            return StockPosition.EMPTY;
        }
        return new StockPosition(
                (BigDecimal) row[0],
                Money.nullSafe((BigDecimal) row[1]));
    }

    @Transactional(readOnly = true)
    public BigDecimal quantityOnHand(Long bookId, Long productId) {
        return positionOf(bookId, productId, LocalDate.now()).quantity();
    }

    /**
     * Total stock value — the figure that must equal the Inventory
     * account's balance in the general ledger. Published as a
     * reconciliation rather than assumed.
     */
    @Transactional(readOnly = true)
    public BigDecimal totalValue(Long bookId, LocalDate asOf) {
        return Money.nullSafe(movements.totalValueAsOf(bookId, asOf));
    }

    // ---------------- expansion ----------------

    /**
     * Resolves what a sold line actually removes from stock.
     * <p>
     * A plain item consumes itself. A combo consumes its components — so
     * a bundle can never be a way to sell stock you do not have, because
     * the availability check runs against the parts, not the wrapper.
     * Services consume nothing.
     */
    public List<Consumption> expand(Product product, BigDecimal quantity) {
        if (product == null || quantity == null || quantity.signum() <= 0) {
            return List.of();
        }
        if (product.isCombo()) {
            List<Consumption> parts = new ArrayList<>();
            for (ProductComponent component : product.getComponents()) {
                Product child = component.getComponent();
                if (child.tracksStock()) {
                    parts.add(new Consumption(child, component.getQuantity().multiply(quantity)));
                }
            }
            return parts;
        }
        return product.tracksStock() ? List.of(new Consumption(product, quantity)) : List.of();
    }

    /**
     * Sums consumption across many lines, so a document listing the same
     * item twice — or two different combos sharing a component — is
     * checked against its true total rather than line by line.
     */
    public Map<Product, BigDecimal> aggregate(List<Consumption> consumptions) {
        Map<Long, Product> byId = new LinkedHashMap<>();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (Consumption c : consumptions) {
            byId.putIfAbsent(c.product().getId(), c.product());
            totals.merge(c.product().getId(), c.quantity(), BigDecimal::add);
        }
        Map<Product, BigDecimal> result = new LinkedHashMap<>();
        totals.forEach((id, qty) -> result.put(byId.get(id), qty));
        return result;
    }

    // ---------------- writing ----------------

    /** Receipt into stock at the price actually paid. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement receive(Book book, Product product, BigDecimal quantity, BigDecimal unitCost,
                                 LocalDate date, StockSource source, Long sourceDocumentId,
                                 JournalEntry entry, String note) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiExceptions.BusinessRuleException("Stock receipt needs a positive quantity");
        }
        BigDecimal cost = Money.of(unitCost == null ? BigDecimal.ZERO : unitCost);
        return movements.save(StockMovement.builder()
                .book(book)
                .product(product)
                .movementDate(date)
                .direction(StockDirection.IN)
                .quantity(quantity)
                .unitCost(cost)
                .totalCost(Money.of(cost.multiply(quantity)))
                .sourceType(source)
                .sourceDocumentId(sourceDocumentId)
                .journalEntry(entry)
                .note(note)
                .build());
    }

    /**
     * Issue out of stock at the weighted average as it stands right now.
     * <p>
     * The average is read immediately before the write and stamped onto
     * the row, so the cost of this sale is fixed at the moment it
     * happened and cannot be restated by a later purchase at a different
     * price.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement issue(Book book, Product product, BigDecimal quantity, LocalDate date,
                               StockSource source, Long sourceDocumentId, JournalEntry entry,
                               String note) {
        // Take the product row before reading the position. Costing is a
        // read-then-write: without the lock, two concurrent sales of the
        // same item both see the same quantity, both pass the
        // availability check, and the shelf goes negative. Locking here
        // makes issues of one product queue rather than race, and costs
        // nothing when there is no contention.
        productRepository.lockById(product.getId());

        StockPosition position = positionOf(book.getId(), product.getId(), LocalDate.now());
        if (!position.canCover(quantity)) {
            throw new ApiExceptions.BusinessRuleException(
                    "Not enough stock of '" + product.getName() + "': need "
                            + quantity.stripTrailingZeros().toPlainString() + ", have "
                            + position.quantity().stripTrailingZeros().toPlainString());
        }

        BigDecimal average = position.averageCost();
        return movements.save(StockMovement.builder()
                .book(book)
                .product(product)
                .movementDate(date)
                .direction(StockDirection.OUT)
                .quantity(quantity)
                .unitCost(average)
                .totalCost(Money.of(average.multiply(quantity)))
                .sourceType(source)
                .sourceDocumentId(sourceDocumentId)
                .journalEntry(entry)
                .note(note)
                .build());
    }

    /**
     * Checks a whole document can be delivered before anything is
     * written, so a five-line order does not half-ship and then fail.
     */
    @Transactional(readOnly = true)
    public void assertAvailable(Book book, Map<Product, BigDecimal> required) {
        List<String> shortages = new ArrayList<>();
        required.forEach((product, quantity) -> {
            StockPosition position = positionOf(book.getId(), product.getId(), LocalDate.now());
            if (!position.canCover(quantity)) {
                shortages.add(product.getName() + " (short by "
                        + position.shortfall(quantity).stripTrailingZeros().toPlainString() + ")");
            }
        });
        if (!shortages.isEmpty()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Not enough stock to deliver: " + String.join(", ", shortages));
        }
    }
}
