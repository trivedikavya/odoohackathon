package com.urbanfurniture.accounting.stock;

import com.urbanfurniture.accounting.identity.Book;
import com.urbanfurniture.accounting.ledger.JournalEntry;
import com.urbanfurniture.accounting.master.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One receipt or issue of stock, in one book.
 * <p>
 * Append-only, like a journal line. Quantity on hand and average cost are
 * aggregated from these rows on every read rather than stored, because a
 * stored quantity becomes a second source of truth the moment anything is
 * back-dated or corrected.
 * <p>
 * {@code unitCost} means different things by direction, and both are
 * needed. On an {@code IN} it is what was actually paid. On an
 * {@code OUT} it is the weighted average <em>at the instant of issue</em>
 * — recorded rather than recomputed, so a historical valuation stays
 * reproducible instead of being silently restated by later purchases.
 */
@Entity
@Table(name = "stock_movement")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockDirection direction;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private StockSource sourceType;

    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    /** The entry that moved the matching value through the general ledger. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @Column(length = 255)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 180, updatable = false)
    private String createdBy;
}
