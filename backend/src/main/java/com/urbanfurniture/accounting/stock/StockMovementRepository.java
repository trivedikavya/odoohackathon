package com.urbanfurniture.accounting.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Quantity and value on hand for one product, as at a date.
     * <p>
     * Returned together in one row because the weighted average is the
     * quotient of the two: fetching them separately would allow a
     * concurrent movement to land between the queries and produce an
     * average that never actually existed.
     * <p>
     * Returns a single row of {@code [quantity, value]}.
     * <p>
     * Declared as a {@code List} rather than a bare {@code Object[]}
     * because Spring Data wraps a multi-column result: asking for
     * {@code Object[]} yields a one-element array whose only member is
     * the row, which reads as an empty result and silently reports zero
     * stock.
     */
    @Query("""
            select coalesce(sum(case when m.direction = com.urbanfurniture.accounting.stock.StockDirection.IN
                                     then m.quantity else -m.quantity end), 0),
                   coalesce(sum(case when m.direction = com.urbanfurniture.accounting.stock.StockDirection.IN
                                     then m.totalCost else -m.totalCost end), 0)
            from StockMovement m
            where m.book.id = :bookId and m.product.id = :productId
              and m.movementDate <= :asOf
            """)
    List<Object[]> positionAsOf(@Param("bookId") Long bookId,
                                @Param("productId") Long productId,
                                @Param("asOf") LocalDate asOf);

    /**
     * The same figures for every product in a book, for the stock ledger
     * report. Returns {@code [productId, name, type, quantity, value]}.
     */
    @Query("""
            select p.id, p.name, p.type,
                   coalesce(sum(case when m.direction = com.urbanfurniture.accounting.stock.StockDirection.IN
                                     then m.quantity else -m.quantity end), 0),
                   coalesce(sum(case when m.direction = com.urbanfurniture.accounting.stock.StockDirection.IN
                                     then m.totalCost else -m.totalCost end), 0)
            from StockMovement m
            join m.product p
            where m.book.id = :bookId and m.movementDate <= :asOf
            group by p.id, p.name, p.type
            order by p.name
            """)
    List<Object[]> positionsAsOf(@Param("bookId") Long bookId, @Param("asOf") LocalDate asOf);

    /**
     * Total value of stock on hand — the figure that must equal the
     * Inventory account's balance in the general ledger.
     */
    @Query("""
            select coalesce(sum(case when m.direction = com.urbanfurniture.accounting.stock.StockDirection.IN
                                     then m.totalCost else -m.totalCost end), 0)
            from StockMovement m
            where m.book.id = :bookId and m.movementDate <= :asOf
            """)
    BigDecimal totalValueAsOf(@Param("bookId") Long bookId, @Param("asOf") LocalDate asOf);

    /** Movement history for one product, newest first. */
    @Query("""
            select m from StockMovement m
            join fetch m.product
            where m.book.id = :bookId and m.product.id = :productId
            order by m.movementDate desc, m.id desc
            """)
    List<StockMovement> historyFor(@Param("bookId") Long bookId, @Param("productId") Long productId);

    /** Units sold per product in a period, for the top-products report. */
    @Query("""
            select p.id, p.name,
                   coalesce(sum(m.quantity), 0),
                   coalesce(sum(m.totalCost), 0)
            from StockMovement m
            join m.product p
            where m.book.id = :bookId
              and m.direction = com.urbanfurniture.accounting.stock.StockDirection.OUT
              and m.sourceType = com.urbanfurniture.accounting.stock.StockSource.DELIVERY
              and m.movementDate >= :from and m.movementDate <= :to
            group by p.id, p.name
            """)
    List<Object[]> unitsSoldBetween(@Param("bookId") Long bookId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
